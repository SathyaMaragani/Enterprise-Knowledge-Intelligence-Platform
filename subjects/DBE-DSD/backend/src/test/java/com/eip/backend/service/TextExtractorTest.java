package com.eip.backend.service;

import com.eip.backend.service.TextExtractor.Format;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Text extraction for each upload format, from files built in memory. */
public class TextExtractorTest {

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";

    @Test
    void formatFollowsTheExtension() {
        assertEquals(Format.PDF, Format.of("Report.PDF"));
        assertEquals(Format.DOCX, Format.of("notes.docx"));
        assertEquals(Format.MD, Format.of("readme.markdown"));
        assertEquals(Format.TXT, Format.of("a.b.txt"));
        assertNull(Format.of("legacy.doc"));
        assertNull(Format.of("no-extension"));
    }

    @Test
    void readsTheTextOfEveryPdfPage() throws IOException {
        String text = TextExtractor.extract(Format.PDF, pdf("Quarterly budget review", "Approved by finance"));

        assertTrue(text.contains("Quarterly budget review"), text);
        assertTrue(text.contains("Approved by finance"), text);
        assertFalse(text.contains("\r"));
    }

    @Test
    void saysWhenAPdfHasNoTextLayer() throws IOException {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TextExtractor.extract(Format.PDF, pdf()));
        assertTrue(e.getMessage().startsWith("No text was found in this PDF"));
    }

    @Test
    void rejectsPasswordProtectedAndDamagedPdfs() throws IOException {
        byte[] locked;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            locked = out.toByteArray();
        }
        assertEquals("Password-protected PDFs cannot be read",
                assertThrows(IllegalArgumentException.class, () -> TextExtractor.extract(Format.PDF, locked)).getMessage());
        assertEquals("The file is not a readable PDF",
                assertThrows(IllegalArgumentException.class,
                        () -> TextExtractor.extract(Format.PDF, "%PDF-1.7 truncated".getBytes(StandardCharsets.US_ASCII)))
                        .getMessage());
    }

    @Test
    void readsDocxParagraphsTabsAndBreaks() throws IOException {
        String xml = "<w:document " + W + "><w:body>"
                + "<w:p><w:r><w:t>Leave</w:t></w:r><w:r><w:t xml:space=\"preserve\"> policy</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Days</w:t><w:tab/><w:t>25</w:t><w:br/><w:t>Carry over: 5</w:t></w:r></w:p>"
                + "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Café &amp; more</w:t></w:r></w:p>"
                + "</w:body></w:document>";

        assertEquals("Leave policy\nDays\t25\nCarry over: 5\nCafé & more\n",
                TextExtractor.extract(Format.DOCX, docx(xml)));
    }

    @Test
    void docxNeverResolvesExternalEntities() throws IOException {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE w:document [<!ENTITY secret SYSTEM \"file:///etc/hostname\">]>"
                + "<w:document " + W + "><w:body><w:p><w:r><w:t>before &secret; after</w:t></w:r></w:p></w:body></w:document>";
        try {
            String text = TextExtractor.extract(Format.DOCX, docx(xml));
            assertTrue(text.startsWith("before"), text);
            assertFalse(text.length() > "before  after\n".length() + 1, "entity was expanded: " + text);
        } catch (IllegalArgumentException rejected) {
            assertEquals("The file is not a readable Word (.docx) document", rejected.getMessage());
        }
    }

    @Test
    void rejectsFilesThatAreNotDocx() throws IOException {
        String message = "The file is not a readable Word (.docx) document";
        assertEquals(message, assertThrows(IllegalArgumentException.class,
                () -> TextExtractor.extract(Format.DOCX, "plain text".getBytes(StandardCharsets.UTF_8))).getMessage());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/styles.xml"));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(message, assertThrows(IllegalArgumentException.class,
                () -> TextExtractor.extract(Format.DOCX, bytes.toByteArray())).getMessage());
    }

    @Test
    void capsTheExtractedText() throws IOException {
        String run = "<w:p><w:r><w:t>" + "x".repeat(1000) + "</w:t></w:r></w:p>";
        String xml = "<w:document " + W + "><w:body>" + run.repeat(1100) + "</w:body></w:document>";

        assertEquals("A document can hold at most 1 MB of text",
                assertThrows(IllegalArgumentException.class, () -> TextExtractor.extract(Format.DOCX, docx(xml)))
                        .getMessage());
    }

    @Test
    void plainTextKeepsItsRules() {
        assertEquals("one\ntwo", TextExtractor.extract(Format.TXT, "﻿one\r\ntwo".getBytes(StandardCharsets.UTF_8)));
        assertEquals("The file must be UTF-8 text", assertThrows(IllegalArgumentException.class,
                () -> TextExtractor.extract(Format.TXT, new byte[]{'c', 'a', 'f', (byte) 0xE9})).getMessage());
        assertEquals("The file has no text", assertThrows(IllegalArgumentException.class,
                () -> TextExtractor.extract(Format.MD, " \n\t ".getBytes(StandardCharsets.UTF_8))).getMessage());
    }

    /** A PDF with one page per line of text; no lines gives a single blank page. */
    public static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            if (pages.length == 0) {
                document.addPage(new PDPage());
            }
            for (String line : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /** A minimal .docx: just the main document part, which is all the extractor reads. */
    public static byte[] docx(String documentXml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
