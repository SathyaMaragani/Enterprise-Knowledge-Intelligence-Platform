package com.eip.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns an uploaded file into plain text: UTF-8 text and Markdown as they are,
 * PDF through PDFBox, and Word (.docx) by reading the document XML inside the zip.
 *
 * <p>Only the text is kept; layout, images and the original file are not stored.
 * Scanned PDFs hold images, not text, and are reported as having none (no OCR).
 */
final class TextExtractor {

    /** A supported file kind: the document type shown in the UI, its MIME type and extractor. */
    enum Format {
        TXT("TXT", "text/plain", "utf8-text-1.0"),
        MD("MD", "text/markdown", "utf8-text-1.0"),
        PDF("PDF", "application/pdf", "pdfbox-3.0"),
        DOCX("DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx-xml-1.0");

        final String documentType;
        final String mimeType;
        final String extractorVersion;

        Format(String documentType, String mimeType, String extractorVersion) {
            this.documentType = documentType;
            this.mimeType = mimeType;
            this.extractorVersion = extractorVersion;
        }

        /** The format for a file name's extension, or null when it is not supported. */
        static Format of(String filename) {
            int dot = filename.lastIndexOf('.');
            return switch (dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
                case "txt" -> TXT;
                case "md", "markdown" -> MD;
                case "pdf" -> PDF;
                case "docx" -> DOCX;
                default -> null;
            };
        }
    }

    /** At most this much extracted text is stored and chunked, whatever the file size. */
    static final int MAX_TEXT_CHARS = 1024 * 1024;
    /** A .docx's document XML may be at most this large once unzipped: a zip bomb stops here. */
    static final long MAX_DOCX_XML_BYTES = 64L * 1024 * 1024;

    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private TextExtractor() {
    }

    /** The file's text, normalised to \n line ends; IllegalArgumentException says what is wrong. */
    static String extract(Format format, byte[] bytes) {
        String text = switch (format) {
            case TXT, MD -> utf8(bytes);
            case PDF -> pdf(bytes);
            case DOCX -> docx(bytes);
        };
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank()) {
            throw new IllegalArgumentException(format == Format.PDF
                    ? "No text was found in this PDF. Scanned PDFs need text recognition, which is not supported."
                    : "The file has no text");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("A document can hold at most 1 MB of text");
        }
        return text;
    }

    private static String utf8(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("The file must be UTF-8 text");
        }
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The file must be UTF-8 text");
        }
        return text;
    }

    private static String pdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Password-protected PDFs cannot be read");
        } catch (IOException e) {
            throw new IllegalArgumentException("The file is not a readable PDF");
        }
    }

    /**
     * Reads word/document.xml: text runs ({@code w:t}), tabs and line breaks, with a
     * line per paragraph. Headers, footers, footnotes and comments live in other parts
     * of the zip and are not included.
     */
    private static String docx(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if ("word/document.xml".equals(entry.getName())) {
                    return documentXmlText(new LimitedInputStream(zip, MAX_DOCX_XML_BYTES));
                }
            }
        } catch (IOException | XMLStreamException e) {
            throw new IllegalArgumentException("The file is not a readable Word (.docx) document");
        }
        throw new IllegalArgumentException("The file is not a readable Word (.docx) document");
    }

    private static String documentXmlText(InputStream xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // No DTDs or external entities: an uploaded file must not make the server fetch or expand anything.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(xml);
        StringBuilder text = new StringBuilder();
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    switch (reader.getLocalName()) {
                        case "t" -> inText = true;
                        case "tab" -> text.append('\t');
                        case "br", "cr" -> text.append('\n');
                        default -> { }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    switch (reader.getLocalName()) {
                        case "t" -> inText = false;
                        case "p" -> text.append('\n');
                        default -> { }
                    }
                } else if (inText && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                    text.append(reader.getText());
                }
                if (text.length() > MAX_TEXT_CHARS) {
                    break; // already over the limit; extract() reports it
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    /** Fails once more than {@code limit} bytes have been read. */
    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public void close() {
            // The zip stream is closed by its owner.
        }

        private void count(int n) throws IOException {
            remaining -= n;
            if (remaining < 0) {
                throw new IOException("Document XML is too large");
            }
        }
    }
}
