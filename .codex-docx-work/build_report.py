from __future__ import annotations

import shutil
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(r"D:\7KLH\S2-Odd\Project-1\enterprise-knowledge-intelligence")
WORK = ROOT / ".codex-docx-work"
REFERENCE = Path(r"C:\Users\lucky\Downloads\Telegram Desktop\PBL Final Documentation template.docx")
OUTPUT = ROOT / "PBL Final Documentation - Enterprise Knowledge Intelligence Platform.docx"
FIGURES = WORK / "figures"

NAVY = "1F4E78"
BLUE = "D9EAF7"
PALE = "EEF5FA"
GRAY = "D9D9D9"
WHITE = "FFFFFF"
BLACK = "000000"


def set_font(run, name="Times New Roman", size=10.5, bold=None, italic=None, color=BLACK):
    run.font.name = name
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), name)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), name)
    run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic
    run.font.color.rgb = RGBColor.from_string(color)
    return run


def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=90, start=110, bottom=90, end=110):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_border(cell, color=GRAY, size="6"):
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = qn(f"w:{edge}")
        node = borders.find(tag)
        if node is None:
            node = OxmlElement(f"w:{edge}")
            borders.append(node)
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), size)
        node.set(qn("w:color"), color)


def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_column_width(cell, width_inches):
    cell.width = Inches(width_inches)
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(int(width_inches * 1440)))
    tc_w.set(qn("w:type"), "dxa")


def add_table(doc, headers, rows, widths, font_size=8.8, aligns=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_repeat_table_header(table.rows[0])
    aligns = aligns or [WD_ALIGN_PARAGRAPH.LEFT] * len(headers)
    for idx, (cell, header, width) in enumerate(zip(table.rows[0].cells, headers, widths)):
        set_column_width(cell, width)
        shade(cell, NAVY)
        set_cell_border(cell)
        set_cell_margins(cell, top=100, bottom=100)
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        p = cell.paragraphs[0]
        p.alignment = aligns[idx]
        p.paragraph_format.space_after = Pt(0)
        set_font(p.add_run(str(header)), size=font_size, bold=True, color=WHITE)
    for r_idx, row_data in enumerate(rows):
        cells = table.add_row().cells
        for c_idx, (cell, value, width) in enumerate(zip(cells, row_data, widths)):
            set_column_width(cell, width)
            set_cell_border(cell)
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            if r_idx % 2:
                shade(cell, PALE)
            p = cell.paragraphs[0]
            p.alignment = aligns[c_idx]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.0
            set_font(p.add_run(str(value)), size=font_size)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return table


def add_complex_field(paragraph, instruction, placeholder="Update field in Word"):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = instruction
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = placeholder
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.extend([begin, instr, separate, text, end])
    return run


def add_caption(doc, label, title):
    p = doc.add_paragraph(style="Caption")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(7)
    set_font(p.add_run(f"{label} "), size=9.5, bold=True)
    add_complex_field(p, f" SEQ {label} \\* ARABIC ", "1")
    set_font(p.add_run(f". {title}"), size=9.5, bold=True)
    return p


def add_figure(doc, image_path, title, width=6.55):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(2)
    shape = p.add_run().add_picture(str(image_path), width=Inches(width))
    shape._inline.docPr.set("descr", title)
    shape._inline.docPr.set("title", title)
    add_caption(doc, "Figure", title)


def add_table_caption(doc, title):
    return add_caption(doc, "Table", title)


def add_body(doc, text, bold_lead=None):
    p = doc.add_paragraph(style="Body Text")
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Inches(0.2)
    p.paragraph_format.line_spacing = 1.05
    p.paragraph_format.space_after = Pt(5)
    if bold_lead and text.startswith(bold_lead):
        set_font(p.add_run(bold_lead), bold=True)
        set_font(p.add_run(text[len(bold_lead):]))
    else:
        set_font(p.add_run(text))
    return p


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style="bullet list")
        p.paragraph_format.left_indent = Inches(0.35)
        p.paragraph_format.first_line_indent = Inches(-0.18)
        p.paragraph_format.space_after = Pt(3)
        set_font(p.add_run(item))


def add_heading(doc, text, level=1):
    style = "Heading 1" if level == 1 else "Heading 2" if level == 2 else "Heading 3"
    p = doc.add_paragraph(style=style)
    # The source template carries an automatic outline list on its heading
    # styles.  This report supplies deliberate Arabic chapter numbers in the
    # heading text, so explicitly suppress inherited list numbering.
    num_pr = OxmlElement("w:numPr")
    num_id = OxmlElement("w:numId")
    num_id.set(qn("w:val"), "0")
    num_pr.append(num_id)
    p._p.get_or_add_pPr().append(num_pr)
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.page_break_before = level == 1 and text.startswith(("4.", "5.", "6.", "7."))
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = p.add_run(text)
    set_font(r, size=11.5 if level == 1 else 10.7, bold=True, italic=(level == 2))
    return p


def add_link(paragraph, text, url):
    part = paragraph.part
    rid = part.relate_to(url, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink", is_external=True)
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), rid)
    run = OxmlElement("w:r")
    rpr = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "0563C1")
    underline = OxmlElement("w:u")
    underline.set(qn("w:val"), "single")
    rpr.extend([color, underline])
    text_node = OxmlElement("w:t")
    text_node.text = text
    run.extend([rpr, text_node])
    hyperlink.append(run)
    paragraph._p.append(hyperlink)


def set_paragraph_text(paragraph, text, size=12, bold=False, italic=False, align=WD_ALIGN_PARAGRAPH.CENTER):
    paragraph.clear()
    paragraph.alignment = align
    paragraph.paragraph_format.space_after = Pt(4)
    set_font(paragraph.add_run(text), size=size, bold=bold, italic=italic)


def remove_after_cover(doc):
    body = doc._body._element
    paragraphs = doc.paragraphs
    for p in paragraphs[24:]:
        body.remove(p._element)
    for table in list(doc.tables):
        body.remove(table._element)


def configure_styles(doc):
    for missing in ("Heading 3", "Caption"):
        try:
            doc.styles[missing]
        except KeyError:
            doc.styles.add_style(missing, WD_STYLE_TYPE.PARAGRAPH)
    for style_name in ("Normal", "Default", "Body Text", "paper title", "Heading 1", "Heading 2", "Heading 3", "Heading 5", "Caption", "references", "bullet list"):
        try:
            style = doc.styles[style_name]
        except KeyError:
            continue
        style.font.name = "Times New Roman"
        style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), "Times New Roman")
        style._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), "Times New Roman")
        style.font.color.rgb = RGBColor(0, 0, 0)
    doc.styles["Normal"].font.size = Pt(10.5)
    doc.styles["Body Text"].font.size = Pt(10.5)
    doc.styles["Heading 1"].font.size = Pt(11.5)
    doc.styles["Heading 1"].font.bold = True
    doc.styles["Heading 2"].font.size = Pt(10.7)
    doc.styles["Heading 2"].font.bold = True
    doc.styles["Heading 2"].font.italic = True
    doc.styles["Heading 3"].font.size = Pt(10.5)
    doc.styles["Heading 3"].font.bold = True
    doc.styles["Caption"].font.size = Pt(9.5)
    doc.styles["Caption"].font.bold = True
    # Remove the template's inherited Roman/letter outline numbering.  Manual
    # chapter and subsection numbers are used throughout this report.
    for style_name in ("Heading 1", "Heading 2", "Heading 3", "references"):
        try:
            p_pr = doc.styles[style_name]._element.get_or_add_pPr()
        except KeyError:
            continue
        num_pr = p_pr.find(qn("w:numPr"))
        if num_pr is not None:
            p_pr.remove(num_pr)


def add_page_field(section, restart=None):
    section.footer.is_linked_to_previous = False
    p = section.footer.paragraphs[0]
    p.clear()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(p.add_run("Page "), size=9)
    field = OxmlElement("w:fldSimple")
    field.set(qn("w:instr"), "PAGE")
    p._p.append(field)
    if restart is not None:
        pg_num_type = OxmlElement("w:pgNumType")
        pg_num_type.set(qn("w:start"), str(restart))
        section._sectPr.append(pg_num_type)


def draw_box(draw, box, text, fill, outline=NAVY, font=None, bold=False):
    fill_color = fill if str(fill).startswith("#") else "#" + str(fill)
    draw.rounded_rectangle(box, radius=18, fill=fill_color, outline="#" + outline, width=4)
    x1, y1, x2, y2 = box
    max_width = x2 - x1 - 30
    words = text.split()
    lines, line = [], ""
    for word in words:
        candidate = (line + " " + word).strip()
        if draw.textbbox((0, 0), candidate, font=font)[2] <= max_width:
            line = candidate
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    heights = [draw.textbbox((0, 0), ln, font=font)[3] for ln in lines]
    total = sum(heights) + 8 * max(0, len(lines) - 1)
    y = y1 + (y2 - y1 - total) / 2
    for ln, h in zip(lines, heights):
        width = draw.textbbox((0, 0), ln, font=font)[2]
        draw.text((x1 + (x2 - x1 - width) / 2, y), ln, fill="#000000", font=font)
        y += h + 8


def arrow(draw, start, end, color=NAVY, width=5):
    draw.line([start, end], fill="#" + color, width=width)
    x, y = end
    sx, sy = start
    if abs(x - sx) > abs(y - sy):
        direction = 1 if x > sx else -1
        pts = [(x, y), (x - direction * 18, y - 11), (x - direction * 18, y + 11)]
    else:
        direction = 1 if y > sy else -1
        pts = [(x, y), (x - 11, y - direction * 18), (x + 11, y - direction * 18)]
    draw.polygon(pts, fill="#" + color)


def font(path, size):
    return ImageFont.truetype(path, size)


def make_figures():
    FIGURES.mkdir(parents=True, exist_ok=True)
    regular = r"C:\Windows\Fonts\times.ttf"
    bold = r"C:\Windows\Fonts\timesbd.ttf"
    f = font(regular, 34)
    fb = font(bold, 36)
    small = font(regular, 29)

    img = Image.new("RGB", (1600, 900), "white")
    d = ImageDraw.Draw(img)
    draw_box(d, (40, 350, 320, 540), "React Frontend", BLUE, font=fb)
    draw_box(d, (470, 300, 920, 590), "Spring Boot REST API\nAuthentication and RBAC\nUnified search and ingestion", PALE, font=f)
    draw_box(d, (300, 45, 650, 205), "TextHack Algorithm Engine", BLUE, font=f)
    draw_box(d, (700, 45, 1050, 205), "ONNX MiniLM Embeddings", BLUE, font=f)
    draw_box(d, (1100, 45, 1450, 205), "ML Insights Pipeline", BLUE, font=f)
    draw_box(d, (300, 700, 650, 855), "PostgreSQL\nMetadata", "E2F0D9", font=small)
    draw_box(d, (700, 700, 1050, 855), "MongoDB\nContent", "FFF2CC", font=small)
    draw_box(d, (1100, 700, 1450, 855), "Qdrant\nVectors", "FCE4D6", font=small)
    arrow(d, (320, 445), (470, 445))
    for x in (475, 875, 1275):
        arrow(d, (695, 300), (x, 205))
    for x in (475, 875, 1275):
        arrow(d, (695, 590), (x, 700))
    img.save(FIGURES / "architecture.png")

    img = Image.new("RGB", (1600, 850), "white")
    d = ImageDraw.Draw(img)
    boxes = [
        ((40, 300, 280, 520), "PDF DOCX TXT MD", BLUE),
        ((350, 300, 620, 520), "Format-aware Text Extraction", PALE),
        ((690, 300, 940, 520), "Normalize and Cap Extracted Text", BLUE),
        ((1010, 300, 1260, 520), "180-word Chunks with 40-word Overlap", PALE),
        ((1330, 150, 1560, 310), "MongoDB\nRaw text and chunks", "FFF2CC"),
        ((1330, 345, 1560, 505), "PostgreSQL\nMetadata and status", "E2F0D9"),
        ((1330, 540, 1560, 700), "Qdrant\n384-D vectors", "FCE4D6"),
    ]
    for box, label, fill in boxes:
        draw_box(d, box, label, fill, font=small if "\n" in label else f)
    arrow(d, (280, 410), (350, 410))
    arrow(d, (620, 410), (690, 410))
    arrow(d, (940, 410), (1010, 410))
    for y in (230, 425, 620):
        arrow(d, (1260, 410), (1330, y))
    img.save(FIGURES / "ingestion.png")

    img = Image.new("RGB", (1600, 900), "white")
    d = ImageDraw.Draw(img)
    draw_box(d, (50, 340, 280, 520), "User Query and Filters", BLUE, font=f)
    draw_box(d, (370, 340, 620, 520), "Access Scope and Permission Filter", PALE, font=f)
    modes = [(730, 70, 990, 220, "Keyword\nTextHack scoring"), (730, 270, 990, 420, "Fuzzy\nEdit distance"), (730, 470, 990, 620, "Semantic\nMiniLM plus Qdrant"), (730, 670, 990, 820, "Hybrid\nFusion")]
    for x1, y1, x2, y2, label in modes:
        draw_box(d, (x1, y1, x2, y2), label, BLUE, font=small)
        arrow(d, (620, 430), (730, (y1 + y2) // 2))
    draw_box(d, (1120, 310, 1380, 580), "Normalize Scores\nMerge by Document\nApply Filters", PALE, font=f)
    draw_box(d, (1450, 340, 1580, 550), "Ranked Results", "E2F0D9", font=small)
    for _, y1, _, y2, _ in modes:
        arrow(d, (990, (y1 + y2) // 2), (1120, 445))
    arrow(d, (1380, 445), (1450, 445))
    img.save(FIGURES / "search.png")

    img = Image.new("RGB", (1600, 820), "white")
    d = ImageDraw.Draw(img)
    draw_box(d, (60, 300, 310, 500), "Browser", BLUE, font=fb)
    draw_box(d, (400, 260, 690, 540), "Vercel\nReact static site\nAPI reverse proxy", PALE, font=f)
    draw_box(d, (790, 260, 1100, 540), "Render\nSpring Boot container\nONNX model", BLUE, font=f)
    stores = [(1220, 60, 1530, 220, "Neon PostgreSQL"), (1220, 330, 1530, 490, "MongoDB Atlas"), (1220, 600, 1530, 760, "Qdrant Cloud")]
    for x1, y1, x2, y2, label in stores:
        draw_box(d, (x1, y1, x2, y2), label, "E2F0D9", font=f)
        arrow(d, (1100, 400), (1220, (y1 + y2) // 2))
    arrow(d, (310, 400), (400, 400))
    arrow(d, (690, 400), (790, 400))
    img.save(FIGURES / "deployment.png")


def build():
    WORK.mkdir(parents=True, exist_ok=True)
    shutil.copy2(REFERENCE, OUTPUT)
    make_figures()
    doc = Document(OUTPUT)
    if doc.inline_shapes:
        doc.inline_shapes[0]._inline.docPr.set("descr", "Koneru Lakshmaiah Education Foundation logo")
        doc.inline_shapes[0]._inline.docPr.set("title", "KLU logo")
    configure_styles(doc)
    remove_after_cover(doc)

    doc.core_properties.title = "Enterprise Knowledge Intelligence Platform Final Project Documentation"
    doc.core_properties.subject = "Project Based Learning final report"
    doc.core_properties.author = "SathyaMaragani"
    doc.core_properties.keywords = "enterprise search, hybrid database, semantic search, TextHack, MiniLM, Spring Boot, React"

    cover = doc.paragraphs[:24]
    set_paragraph_text(cover[1], "Enterprise Knowledge Intelligence Platform", size=20)
    set_paragraph_text(cover[2], "A Project Based Learning Report submitted in partial fulfilment of the requirements", size=13, italic=True)
    set_paragraph_text(cover[4], "for the award of the degree", size=13, italic=True)
    set_paragraph_text(cover[6], "Bachelor of Technology", size=15, bold=True)
    set_paragraph_text(cover[7], "Interdisciplinary Project in", size=13, bold=True)
    set_paragraph_text(cover[8], "Database Systems, Algorithms, Operating Systems and Machine Learning", size=13, bold=True)
    set_paragraph_text(cover[10], "Submitted by", size=12)
    set_paragraph_text(cover[11], "SathyaMaragani", size=13, bold=True)
    for idx in (12, 13, 14, 15, 16, 17, 19):
        set_paragraph_text(cover[idx], "", size=10)
    set_paragraph_text(cover[20], "Koneru Lakshmaiah Education Foundation", size=12)
    set_paragraph_text(cover[21], "Aziz Nagar", size=12)
    set_paragraph_text(cover[22], "Aziz Nagar - 500075", size=12)
    set_paragraph_text(cover[23], "October 2026", size=12)
    sect_type = cover[23]._p.pPr.sectPr.find(qn("w:type"))
    if sect_type is None:
        sect_type = OxmlElement("w:type")
        cover[23]._p.pPr.sectPr.insert(0, sect_type)
    sect_type.set(qn("w:val"), "nextPage")

    # Section 2: Abstract
    heading = doc.add_paragraph()
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    heading.paragraph_format.space_after = Pt(14)
    set_font(heading.add_run("Abstract"), size=16, bold=True)
    abstract = (
        "The Enterprise Knowledge Intelligence Platform is an integrated system for uploading, organizing, "
        "protecting, searching, analyzing and retrieving organizational knowledge. It combines a React user "
        "interface with a Spring Boot service and assigns distinct storage responsibilities to PostgreSQL, "
        "MongoDB and Qdrant. PostgreSQL is the relational source of truth for identities, roles, permissions, "
        "document metadata and search history; MongoDB stores extracted text, processing metadata and chunks; "
        "Qdrant stores 384-dimensional MiniLM vectors for semantic retrieval. The platform supports keyword, "
        "fuzzy, semantic and hybrid search, role-based access control, document sharing, administration, "
        "activity history and a document viewer. Its upload pipeline accepts PDF, DOCX, TXT and Markdown files, "
        "extracts text securely, chunks it into overlapping windows and indexes it for retrieval. The TextHack "
        "engine contributes 20 algorithms implemented from first principles, while the machine-learning work "
        "evaluates retrieval, category classification and clustering with leakage-resistant protocols. The "
        "OSSP contribution, ShellForge, demonstrates process creation, signal handling, pipes, redirection and "
        "memory-safe systems programming. The deployed application runs on Vercel and Render with Neon, MongoDB "
        "Atlas and Qdrant Cloud. Verification includes 191 backend tests, 148 frontend tests, 452 TextHack "
        "assertions, 148 ShellForge assertions, machine-learning checks and 28 deployment smoke checks. The "
        "result is a working, deployed academic platform that demonstrates how databases, algorithms, operating "
        "systems and machine learning can be combined into one coherent knowledge-retrieval product."
    )
    add_body(doc, abstract)

    # Section 3: List of Figures
    doc.add_section(WD_SECTION.NEW_PAGE)
    heading = doc.add_paragraph()
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(heading.add_run("List of Figures"), size=16, bold=True)
    lof = doc.add_paragraph()
    add_complex_field(lof, ' TOC \\h \\z \\c "Figure" ', "Figure list updates when opened in Word")

    # Section 4: List of Tables
    doc.add_section(WD_SECTION.NEW_PAGE)
    heading = doc.add_paragraph()
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(heading.add_run("List of Tables"), size=16, bold=True)
    lot = doc.add_paragraph()
    add_complex_field(lot, ' TOC \\h \\z \\c "Table" ', "Table list updates when opened in Word")

    # Section 5: Table of Contents
    doc.add_section(WD_SECTION.NEW_PAGE)
    heading = doc.add_paragraph()
    heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(heading.add_run("Table of Contents"), size=16, bold=True)
    toc = doc.add_paragraph()
    add_complex_field(toc, ' TOC \\o "1-3" \\h \\z \\u ', "Contents update when opened in Word")

    # Section 6: Main report
    doc.add_section(WD_SECTION.NEW_PAGE)
    main_title = doc.add_paragraph(style="paper title")
    main_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(main_title.add_run("Enterprise Knowledge Intelligence Platform"), size=24)

    add_heading(doc, "1. INTRODUCTION", 1)
    add_heading(doc, "1.1 Background and Motivation", 2)
    add_body(doc, "Organizations accumulate policies, procedures, research notes, technical guides and operational records across many repositories. Conventional file storage preserves the documents but does not provide a unified way to understand their content, enforce document-level access or find material by meaning. This project addresses that gap with an enterprise knowledge platform that combines structured metadata, full document text, vector embeddings and algorithmic text analysis behind one authenticated web interface.")
    add_body(doc, "The project is deliberately interdisciplinary. Database engineering provides consistent identity and metadata management; data structures and algorithms provide transparent search and analysis primitives; operating-systems work demonstrates the low-level process and IPC foundations used by software platforms; and machine learning supplies measured semantic retrieval, classification and clustering. The completed repository therefore serves both as a usable application and as evidence for four subject areas.")

    add_heading(doc, "1.2 Problem Statement", 2)
    add_body(doc, "The central problem is to let authorized users store heterogeneous documents and retrieve the right information without forcing every question into exact keywords. The system must reconcile three kinds of data: relational business metadata and permissions, unstructured document content, and numerical embedding vectors. It must also remain testable, explainable and deployable on limited infrastructure.")

    add_heading(doc, "1.3 Project Objectives", 2)
    add_bullets(doc, [
        "Provide secure authentication, role-based authorization and document-level sharing.",
        "Accept PDF, DOCX, plain-text and Markdown documents and convert their text into a common searchable representation.",
        "Implement keyword, fuzzy, semantic and hybrid search with consistent permission filtering.",
        "Store relational metadata, document content and vector representations in databases suited to each responsibility.",
        "Implement and demonstrate core algorithms without replacing them with library shortcuts.",
        "Evaluate retrieval, classification and clustering using reproducible datasets and leakage-resistant protocols.",
        "Build a Unix-like teaching shell that demonstrates processes, signals, pipes, redirection and memory discipline.",
        "Deliver a responsive web interface, automated tests, containerized deployment and a live academic demonstration."
    ])

    add_heading(doc, "1.4 Project Scope and Workstreams", 2)
    add_table_caption(doc, "Interdisciplinary workstreams and responsibilities")
    add_table(doc,
        ["Workstream", "Primary responsibility", "Implemented outcome"],
        [
            ["DBE-DSD", "Databases, backend APIs, security and frontend", "Hybrid persistence, Spring Boot REST API, React application and deployment"],
            ["DSA-3", "Algorithms and complexity analysis", "TextHack engine with 20 algorithms, demonstrations and complexity registry"],
            ["OSSP", "Operating-systems and systems-programming concepts", "ShellForge v9 with processes, signals, pipes, redirection and diagnostics"],
            ["Machine Learning", "Embeddings, retrieval evaluation, classification and clustering", "MiniLM pipeline, FiQA evaluation and ML insights page"],
        ], [1.0, 2.3, 3.55])
    add_body(doc, "Project contributor: SathyaMaragani. The repository records the four workstreams above as the project organization and integrates their outputs where appropriate. TextHack and the ONNX embedding path run inside the web backend; ShellForge remains a standalone systems-programming deliverable in the same repository.")

    add_heading(doc, "2. SYSTEM DESIGN AND METHODOLOGY", 1)
    add_heading(doc, "2.1 Overall Architecture", 2)
    add_body(doc, "The user interacts with a React single-page application. Requests pass through a Spring Boot API that authenticates the caller, applies role and document-level access rules, coordinates search or ingestion and composes responses from the data stores. The design avoids treating one database as a universal solution: each store owns the data shape and access pattern it handles best.")
    add_figure(doc, FIGURES / "architecture.png", "Logical architecture of the integrated platform")

    add_table_caption(doc, "Technology stack")
    add_table(doc,
        ["Layer", "Technology", "Purpose"],
        [
            ["Frontend", "React 19, React Router, Vite 8", "Responsive authenticated single-page application"],
            ["Backend", "Java 21, Spring Boot 3.4.2", "REST API, security, orchestration and health endpoints"],
            ["Relational data", "PostgreSQL 16", "Users, roles, permissions, metadata, versions, grants and history"],
            ["Document data", "MongoDB 7", "Extracted content, chunks, metadata and processing records"],
            ["Vector data", "Qdrant", "384-dimensional cosine-similarity vectors and search filters"],
            ["Embedding", "ONNX Runtime and DJL tokenizer", "In-process MiniLM query and chunk encoding"],
            ["Algorithms", "Plain Java TextHack", "Exact, fuzzy, alignment, flow, approximation and randomized methods"],
            ["Systems", "C and POSIX APIs", "ShellForge process, IPC, signal and redirection features"],
            ["Delivery", "Docker, GitHub Actions, Vercel and Render", "Repeatable testing and cloud deployment"],
        ], [1.15, 2.15, 3.55])

    add_heading(doc, "2.2 Hybrid Database Responsibility Model", 2)
    add_table_caption(doc, "Storage responsibilities")
    add_table(doc,
        ["Store", "Authoritative data", "Key identifiers and indexes"],
        [
            ["PostgreSQL", "Users, roles, permissions, categories, documents, tags, versions, grants and search history", "documents.id links every store; indexed ownership, category, status and user relationships"],
            ["MongoDB", "Raw extracted text, word/character counts, source details, chunks, processing metadata and version snapshot", "Unique postgres_document_id; title, department, keywords, status, version and timestamp indexes"],
            ["Qdrant", "One normalized 384-D vector per chunk with denormalized filter payload", "postgres_document_id, category, department, language and processing_status payload indexes"],
        ], [1.2, 3.25, 2.4])
    add_body(doc, "Cross-store identity is explicit. PostgreSQL document identifiers are stored in MongoDB as postgres_document_id and in every Qdrant payload. MongoDB chunk identifiers such as doc7-chunk3 are repeated in Qdrant, allowing a semantic hit to be joined back to the readable chunk and relational metadata.")

    add_heading(doc, "2.3 Document Ingestion Method", 2)
    add_body(doc, "An upload is validated before any persistent record is created. The file name selects a format-specific extractor. UTF-8 decoding handles TXT and Markdown, Apache PDFBox extracts text layers from PDF files, and a ZIP plus StAX reader extracts WordprocessingML text from DOCX files. External entities and DTD processing are disabled for DOCX, decompressed XML is bounded, password-protected or corrupt PDFs are rejected, and image-only PDFs receive a clear no-text error because OCR is outside the current scope.")
    add_figure(doc, FIGURES / "ingestion.png", "Document ingestion, chunking and indexing workflow")
    add_body(doc, "Files may be at most 10 MB and extracted text may be at most 1 MB. After line-ending normalization and blank-content checks, text is divided into 180-word windows with a 40-word overlap. PostgreSQL first records the document in PROCESSING state; MongoDB receives the content and chunks; PostgreSQL receives version 1; and Qdrant receives embeddings when the model and vector store are available. The final state is INDEXED when vectors are stored or UPLOADED when content is safe but semantic indexing is temporarily unavailable. Compensating cleanup removes earlier writes if a later non-vector step fails.")

    add_heading(doc, "2.4 Search Methodology", 2)
    add_figure(doc, FIGURES / "search.png", "Unified search modes, permission filtering and ranking")
    add_table_caption(doc, "Search modes")
    add_table(doc,
        ["Mode", "Method", "Best use"],
        [
            ["Keyword", "TextHack exact and multi-pattern scoring over metadata", "Known terms, names and phrases"],
            ["Fuzzy", "Damerau-Levenshtein-aware lexical scoring", "Typos, transpositions and near matches"],
            ["Semantic", "MiniLM query embedding and Qdrant cosine search", "Conceptual similarity and paraphrases"],
            ["Hybrid", "Normalized fusion of lexical and vector evidence", "Balanced general search"],
        ], [1.0, 3.15, 2.7])
    add_body(doc, "Every mode applies the same access model. Administrators can see all documents; other users see documents they own or documents explicitly shared with them. Category and status filters are applied consistently. Search activity is recorded without allowing logging failures to break a successful search response.")

    add_heading(doc, "2.5 Security and Access Model", 2)
    add_body(doc, "Authentication uses signed JSON Web Tokens. Passwords are stored as BCrypt hashes. The API exposes a minimal public surface: login and health checks; document, search, administration and sharing operations require authentication and method-level authorities. Seed roles grant administrators full management, managers document creation and employees read access within ownership or explicit grants. Disabled accounts are rejected even when a previously issued token exists.")
    add_bullets(doc, [
        "Administrative APIs create users, change roles, enable or disable accounts and reset passwords.",
        "Document grants support listing, creation and revocation of READ access.",
        "Validation and global exception handling return stable error structures without leaking stack traces.",
        "Production health output hides internal component details while dedicated tests prevent accidental exposure.",
        "The frontend derives visible actions from the authenticated profile, but backend authorization remains authoritative."
    ])

    add_heading(doc, "3. IMPLEMENTATION", 1)
    add_heading(doc, "3.1 Backend and API", 2)
    add_body(doc, "The backend follows controller, service and repository layers. Controllers map HTTP requests and responses; services own orchestration, authorization and business rules; repositories isolate PostgreSQL, MongoDB and Qdrant access. The API covers authentication and profiles, health, document listing and pagination, upload and deletion, categories, document grants, administration, vector search, unified search, search history and TextHack demonstrations.")
    add_table_caption(doc, "Representative REST endpoints")
    add_table(doc,
        ["Area", "Endpoint examples", "Behavior"],
        [
            ["Authentication", "POST /api/auth/login; GET /api/auth/me", "Issue token and return the current profile"],
            ["Documents", "GET/POST /api/documents; GET/DELETE /api/documents/{id}", "List, upload, view and remove authorized documents"],
            ["Search", "POST /api/search; GET /api/search/history", "Run four search modes and retrieve recent activity"],
            ["Sharing", "/api/documents/{id}/permissions", "List, grant and revoke document access"],
            ["Administration", "/api/admin/users and /api/admin/roles", "Manage users, roles, status and passwords"],
            ["TextHack", "/api/texthack/*", "Pattern, similarity, citation and complexity demonstrations"],
        ], [1.05, 3.2, 2.6], font_size=8.5)

    add_heading(doc, "3.2 Frontend Experience", 2)
    add_body(doc, "The React application provides sign-in, a role-aware dashboard, global search, repository browsing, document viewing, upload, sharing, administration, TextHack demonstrations and ML insights. Search mode is represented in the URL so results remain reproducible. The dashboard presents recent documents and activity, while the repository offers pagination and filters. The document page exposes source, content, chunk and processing details when the caller is authorized.")
    add_body(doc, "The upload page mirrors server-side extension and size checks, accepts drag-and-drop entry from the dashboard, and explains that scanned PDFs require OCR. A server-gate component handles Render cold starts by polling health before allowing normal requests. Accessibility-oriented labels, responsive layouts down to mobile width, consistent badges and explicit empty/error states are covered by frontend tests.")

    add_heading(doc, "3.3 TextHack Algorithm Engine", 2)
    add_body(doc, "TextHack is dependency-free Java compiled directly into the Spring backend as a second source root. Core algorithms avoid java.util data structures and are accompanied by explicit time/space analysis. The engine supports exact pattern search, multi-pattern search, fuzzy similarity, sequence alignment, graph flow, approximation and randomized demonstrations.")
    add_table_caption(doc, "Implemented TextHack algorithms")
    add_table(doc,
        ["Category", "Algorithms", "Key complexity or guarantee"],
        [
            ["String matching", "Naive, KMP, Z, Rabin-Karp, Aho-Corasick", "KMP/Z O(n); Aho-Corasick O(n + matches)"],
            ["Suffix structures", "Prefix-doubling suffix array, Kasai LCP", "O(n log n) and O(n)"],
            ["Dynamic programming", "Levenshtein, two Damerau variants, Needleman-Wunsch, Smith-Waterman", "O(nm); Levenshtein uses O(min(n,m)) space"],
            ["Network flow", "Ford-Fulkerson, Edmonds-Karp, Dinic, bipartite matching", "Multiple strategies and graph guarantees"],
            ["Approximation", "Vertex cover, list scheduling, LPT", "2-approximation and scheduling bounds"],
            ["Randomized", "Miller-Rabin, universal hashing, reservoir sampling, SplitMix64", "Exact 64-bit primality witness set; one-pass sampling"],
        ], [1.35, 3.65, 1.85], font_size=8.3)
    add_body(doc, "The six TextHack suites contain 452 assertions with zero failures and zero compiler warnings. Randomized cross-validation generates 4,000 string-matching cases and requires Naive, KMP, Z and Rabin-Karp implementations to agree. Tests include overlapping matches, repetitive worst cases, Unicode, embedded NUL characters, Carmichael numbers and flow cases that require reverse edges.")

    add_heading(doc, "3.4 Machine Learning Pipeline", 2)
    add_body(doc, "Semantic retrieval uses sentence-transformers/all-MiniLM-L6-v2 through ONNX Runtime inside Java. The DJL tokenizer produces input identifiers, attention masks and token-type identifiers; the backend performs attention-mask-aware mean pooling and L2 normalization. This makes dot product equal cosine similarity in Qdrant. Java ONNX output was numerically matched against Python, with approximately 1.5e-7 maximum element difference and identical top-five retrieval across 32 regression queries over 705 vectors.")
    add_body(doc, "The ML work separates deterministic database fixtures from the retrieval corpus. FiQA-2018 supplies 57,638 documents, 648 test queries and 1,706 relevance judgments. A bounded, reproducible subset of 150 queries and 7,087 chunks compares TF-IDF, MiniLM and BGE-small. Dense models significantly outperform TF-IDF; MiniLM is selected provisionally because it is about 2.5 times faster and its difference from BGE is not statistically significant on the bounded subset.")
    add_table_caption(doc, "Retrieval evaluation on the bounded FiQA subset")
    add_table(doc,
        ["Model", "nDCG@10", "MRR", "Recall@10"],
        [
            ["TF-IDF", "0.3709", "0.4182", "0.4617"],
            ["MiniLM-L6", "0.6240", "0.7120", "0.6739"],
            ["BGE-small", "0.6533", "0.7322", "0.7089"],
        ], [2.5, 1.45, 1.45, 1.45], aligns=[WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER])

    add_heading(doc, "3.5 Classification, Clustering and Insights", 2)
    add_body(doc, "The demo corpus contains 315 policy documents: 21 topics, six organizational categories and 15 near-identical versions per topic. Random document splits would leak topic copies into both training and validation, so evaluation groups by topic. Character TF-IDF with multinomial Naive Bayes is selected after leave-one-topic-out validation. It scores 0.83 accuracy and 0.78 macro-F1 on six locked unseen topics, but 0.57 accuracy across all 21 leave-one-topic-out folds. The difference is reported rather than hidden because the independent sample is small and category labels are organizational rather than purely topical.")
    add_body(doc, "Unsupervised analysis compares k-means variants, agglomerative clustering, mini-batch k-means and DBSCAN. DBSCAN automatically finds 21 topic clusters with topic ARI 0.98 and 11 noise documents. At six clusters, k-means on a 17-component PCA representation achieves category ARI 0.60. The frontend Insights page displays the generated results, including category performance, cluster structure and t-SNE visualization data.")

    add_heading(doc, "3.6 ShellForge Systems Programming", 2)
    add_body(doc, "ShellForge is a Unix-like shell implemented in C as the OSSP contribution. Version 9 implements a dynamic REPL and parser; built-ins such as cd, pwd, env, clear, help and exit; fork, execvp and waitpid execution; SIGINT, SIGCHLD and SIGTSTP handling; two-process pipelines; and input, output, append and error redirection. File descriptors are closed deliberately to prevent deadlocks, child signals are restored to defaults, and SIGCHLD is blocked around foreground waits so exit status is not lost.")
    add_body(doc, "The project provides AddressSanitizer, Valgrind and GDB workflows and checks every allocation and system call. Six weekly suites total 148 passing assertions. It builds with GCC 13 in a Linux container on Windows and requires no change to build on a native Linux host.")

    add_heading(doc, "4. EXPERIMENTS AND VALIDATION", 1)
    add_heading(doc, "4.1 Automated Test Strategy", 2)
    add_body(doc, "Validation combines pure unit tests, database-backed integration tests, randomized algorithm checks, model comparisons, production-profile checks, frontend component tests and deployed-stack smoke tests. Integration tests use seeded PostgreSQL, MongoDB and Qdrant services and remove temporary uploads so fixture state remains stable.")
    add_table_caption(doc, "Verification summary")
    add_table(doc,
        ["Subsystem", "Evidence", "Result"],
        [
            ["Backend", "Unit plus PostgreSQL/MongoDB/Qdrant integration and demo corpus", "191 tests passing"],
            ["Frontend", "Vitest component and behavior suite", "148 tests passing; production build succeeds"],
            ["TextHack", "Six algorithm suites and randomized cross-validation", "452 assertions passing; zero lint warnings"],
            ["ShellForge", "Weeks 4-9 shell suites", "148 assertions passing"],
            ["Machine learning", "Pipeline, embedding structure/behavior and insight consistency", "25 offline checks plus two model-dependent checks"],
            ["Deployment", "Nginx/Vercel path, auth, search, TextHack and resource tests", "28 of 28 smoke checks passing"],
        ], [1.25, 3.65, 1.8], font_size=8.5)

    add_heading(doc, "4.2 PDF and DOCX Validation", 2)
    add_body(doc, "The document extractor tests build real PDF files and DOCX ZIP packages in memory. They verify multi-page PDF extraction, Word paragraphs, tabs and line breaks, Unicode, corrupt files, password protection, image-only PDFs, extension mapping and the extracted-text cap. A crafted external-entity DOCX confirms that the XML parser never reads local or remote entity content. API integration tests then upload both formats, verify document type and MIME metadata, inspect stored text and chunks and clean up every created record.")

    add_heading(doc, "4.3 Retrieval and Model Evaluation", 2)
    add_body(doc, "Retrieval quality uses Recall at K, mean reciprocal rank and normalized discounted cumulative gain. Paired bootstrap testing shows both dense models beat TF-IDF with p < 0.0001, while BGE-small versus MiniLM is not significant at p = 0.1383. Results on the bounded subset are intentionally labeled as inflated relative to the full corpus and are not presented as published BEIR scores.")
    add_body(doc, "Classification evaluation locks one topic per category before tuning and repeats leave-one-topic-out evaluation across all 21 topics. Clustering is evaluated against both category labels and topic labels using adjusted Rand index, normalized mutual information, homogeneity, completeness and silhouette. This protocol exposes the difference between memorizing near-duplicates and generalizing to unseen topics.")

    add_heading(doc, "4.4 Resource and Deployment Testing", 2)
    add_body(doc, "Under Render's 512 MB and 0.1 CPU free-tier cap, memory rose from 391 MiB after startup to 446 MiB after repeated searches, 16 concurrent semantic searches and a 950 KB upload, without an out-of-memory kill. Timings were 0.2-0.4 seconds for keyword, 0.4-0.9 seconds for semantic and 0.5-0.6 seconds for hybrid search; cold starts are slower while the JVM and model reload.")

    add_heading(doc, "5. RESULTS AND DISCUSSION", 1)
    add_heading(doc, "5.1 Completed Capabilities", 2)
    add_bullets(doc, [
        "A live authenticated platform with dashboard, search, repository, document viewer, upload, sharing, administration, TextHack and ML insights pages.",
        "Hybrid persistence across PostgreSQL, MongoDB and Qdrant with stable cross-store identifiers and compensating cleanup.",
        "Four search modes that combine transparent lexical algorithms with semantic retrieval while enforcing one access policy.",
        "In-process MiniLM embeddings with Python/Java numerical equivalence and no separate model microservice.",
        "Secure PDF and DOCX text extraction in addition to UTF-8 text and Markdown uploads.",
        "A comprehensive first-principles algorithm library and a standalone systems-programming shell.",
        "Leakage-aware ML evaluation that reports both strong results and uncertainty instead of relying on a misleading random split.",
        "Automated CI gates and a resource-constrained cloud deployment verified through the public frontend and backend."
    ])

    add_heading(doc, "5.2 Quantitative Outcomes", 2)
    add_table_caption(doc, "Selected project outcomes")
    add_table(doc,
        ["Measure", "Outcome", "Interpretation"],
        [
            ["TextHack coverage", "20 algorithms; 452 assertions", "Broad algorithmic scope with explicit correctness and complexity evidence"],
            ["Semantic retrieval", "MiniLM nDCG@10 0.6240", "Substantial improvement over TF-IDF 0.3709 on bounded FiQA subset"],
            ["Classification", "0.83 locked-test accuracy; 0.57 across 21 topics", "Promising but sensitive to which independent topics are held out"],
            ["Unsupervised topics", "DBSCAN 21 clusters; topic ARI 0.98", "Embeddings separate the synthetic policy topics strongly"],
            ["Backend quality", "191 tests", "Business logic, security, databases, search and deployment profile covered"],
            ["Deployment footprint", "Approx. 446 MiB under stress", "Fits the 512 MB Render free tier with limited margin"],
        ], [1.45, 2.1, 3.15], font_size=8.5)

    add_heading(doc, "5.3 Limitations", 2)
    add_bullets(doc, [
        "Scanned or image-only PDFs require OCR, which is not implemented. Password-protected PDFs are rejected.",
        "The 10 MB file cap and 1 MB extracted-text cap bound memory and indexing cost but exclude very large documents.",
        "Embedding runs synchronously in the upload request; large documents can exceed a free proxy's request timeout even if backend processing later completes.",
        "The deployment relies on free services that sleep, suspend or delete idle resources. Cold starts can exceed 100 seconds.",
        "The 315-document insights corpus is synthetic and contains near-duplicate versions; its results demonstrate method correctness, not production accuracy on a real enterprise corpus.",
        "The bounded FiQA comparison is reproducible and statistically tested but does not replace full-corpus benchmarking.",
        "ShellForge is a standalone OSSP deliverable and is not exposed as a production web shell, which avoids a serious remote-command security risk.",
        "The Indian-language Wikipedia corpus originally considered for TextHack remains outside scope because acquisition, licensing and storage require a separate data decision."
    ])

    add_heading(doc, "6. DEPLOYMENT AND OPERATIONS", 1)
    add_heading(doc, "6.1 Cloud Topology", 2)
    add_figure(doc, FIGURES / "deployment.png", "Cloud deployment topology")
    add_table_caption(doc, "Production services")
    add_table(doc,
        ["Component", "Service", "Operational role"],
        [
            ["Frontend", "Vercel Hobby", "Builds the React application and proxies /api to the backend"],
            ["Backend", "Render free web service", "Runs the Spring Boot container, TextHack and ONNX MiniLM"],
            ["Relational database", "Neon PostgreSQL", "Pooled SQL connection for identity and metadata"],
            ["Document database", "MongoDB Atlas", "Managed storage for content and chunks"],
            ["Vector database", "Qdrant Cloud", "Managed cosine search with gRPC/TLS access"],
            ["Continuous integration", "GitHub Actions", "Runs frontend and backend quality gates before deployment"],
        ], [1.35, 2.0, 3.35])

    add_heading(doc, "6.2 Production Safeguards", 2)
    add_bullets(doc, [
        "The backend Docker image pins and checksum-verifies the tokenizer and ONNX model.",
        "The production JVM uses a constrained heap, C1-only compilation, two malloc arenas and a class-data-sharing archive to fit the memory budget.",
        "Render exposes /api/health and limits request threads and database connections.",
        "Vercel rejects a missing or malformed backend origin at build time and disables caching for per-user API responses.",
        "The frontend waits for the sleeping backend to become healthy instead of immediately presenting an authentication failure.",
        "The first administrator is created from temporary environment variables only when no administrator exists; the variables are removed after bootstrap."
    ])

    add_heading(doc, "6.3 Live Availability", 2)
    p = add_body(doc, "The production user interface is available at ")
    add_link(p, "https://ekipsearch.vercel.app", "https://ekipsearch.vercel.app")
    set_font(p.add_run(". The public backend health endpoint is available at "))
    add_link(p, "https://enterprise-knowledge-intelligence-0hg2.onrender.com/api/health", "https://enterprise-knowledge-intelligence-0hg2.onrender.com/api/health")
    set_font(p.add_run(". The PDF and DOCX release was deployed from commit 69e5e43 after both frontend and backend GitHub workflows passed."))

    add_heading(doc, "7. CONCLUSION AND FUTURE WORK", 1)
    add_heading(doc, "7.1 Conclusion", 2)
    add_body(doc, "The project delivers a working enterprise knowledge platform rather than a collection of disconnected subject exercises. Its design assigns metadata, content and vectors to suitable data stores; exposes them through a secured service; combines exact, fuzzy and semantic evidence in search; and presents the result through a responsive web interface. TextHack, ShellForge and the ML experiments provide independent algorithmic, systems and analytical depth, while the deployed application proves that the major parts can operate together under strict resource limits.")
    add_body(doc, "The strongest result is not a single metric. It is the combination of explainable algorithms, reproducible evaluation, defensive ingestion, permission-aware retrieval, automated testing and live deployment. The documented limitations also identify where academic demonstration ends and production hardening would begin.")

    add_heading(doc, "7.2 Future Work", 2)
    add_bullets(doc, [
        "Add OCR for scanned PDFs with explicit language selection and confidence reporting.",
        "Move large-document embedding to a background job and expose retry/progress state.",
        "Evaluate quantized MiniLM if future memory pressure outweighs the cost of rebuilding vectors.",
        "Evaluate category suggestions on real organizational documents before enabling automated filing.",
        "Run full-corpus FiQA evaluation and extend retrieval tests to multilingual and domain-specific corpora.",
        "Add document re-indexing, vector rebuild and retention workflows for model upgrades.",
        "Add audit reporting for grants, administrative changes and sensitive document access.",
        "Extend ShellForge with job control, process groups, FIFOs and virtual-memory demonstrations while keeping it isolated from the public web application."
    ])

    # Section 7: References
    doc.add_section(WD_SECTION.NEW_PAGE)
    add_heading(doc, "REFERENCES", 1)
    refs = [
        "Enterprise Knowledge Intelligence Platform repository and project documentation, GitHub, 2026.",
        "N. Thakur, N. Reimers, A. Rucklé, A. Srivastava and I. Gurevych, 'BEIR: A Heterogeneous Benchmark for Zero-shot Evaluation of Information Retrieval Models,' NeurIPS Datasets and Benchmarks, 2021.",
        "N. Reimers and I. Gurevych, 'Sentence-BERT: Sentence Embeddings using Siamese BERT-Networks,' EMNLP-IJCNLP, 2019.",
        "Spring Boot Reference Documentation, https://docs.spring.io/spring-boot/.",
        "PostgreSQL Documentation, https://www.postgresql.org/docs/.",
        "MongoDB Manual, https://www.mongodb.com/docs/.",
        "Qdrant Documentation, https://qdrant.tech/documentation/.",
        "Apache PDFBox Documentation, https://pdfbox.apache.org/.",
        "ONNX Runtime Documentation, https://onnxruntime.ai/docs/.",
        "Project records: backend API, testing, ML evaluation, TextHack complexity, ShellForge test and cloud deployment documentation in the repository."
    ]
    for i, ref in enumerate(refs, 1):
        p = doc.add_paragraph(style="references")
        num_pr = OxmlElement("w:numPr")
        num_id = OxmlElement("w:numId")
        num_id.set(qn("w:val"), "0")
        num_pr.append(num_id)
        p._p.get_or_add_pPr().append(num_pr)
        p.paragraph_format.left_indent = Inches(0.25)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        set_font(p.add_run(f"[{i}]  {ref}"), size=8.5)

    # Page numbers from abstract onward; cover footer remains blank.
    for i, section in enumerate(doc.sections):
        if i == 0:
            section.footer.is_linked_to_previous = False
            section.footer.paragraphs[0].clear()
        else:
            add_page_field(section, restart=1 if i == 1 else None)

    # Ask Word to refresh TOC/list/page/SEQ fields when opened.
    settings = doc.settings._element
    update = settings.find(qn("w:updateFields"))
    if update is None:
        update = OxmlElement("w:updateFields")
        settings.append(update)
    update.set(qn("w:val"), "true")

    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    build()
