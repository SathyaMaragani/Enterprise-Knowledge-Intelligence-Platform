# Template execution contract

## Reference

- Source: `C:\Users\lucky\Downloads\Telegram Desktop\PBL Final Documentation template.docx`
- SHA-256: `9BE98EEFB5DF60AD97E321E20D4713F7FBAA00AF28ADE008AB64323FA8C9C3D5`
- Rendered baseline: 8 pages in `.codex-docx-work/template-reference-render`
- Sections: 7
- Evidence: `template-style-evidence.json`, section audit output, heading/image/field/content-control audits, and Word-rendered baseline PDF

## Page system

- A4 portrait throughout, 8.27 x 11.69 inches.
- Cover and front matter use 0.62 inch side margins, 0.31 inch top margin, and 1.00 inch bottom margin.
- Main report uses approximately 0.63 inch side margins, 0.75 inch top margin, and 1.00 inch bottom margin.
- The source has seven sections using a mixture of new-page and continuous breaks. No headers, footer text, odd/even variation, or first-page variation.
- The completed report may add pages to accommodate real content, but retains A4 geometry, portrait orientation, source margins, and a cover/front-matter/main-body flow.

## Typography

- Visual authority: Times New Roman family, black text.
- Cover title: centered, 20 pt, regular.
- Main paper title: centered, 24 pt.
- Cover metadata: centered, 12-13 pt, selective bold and italic emphasis.
- Main body: justified, approximately 11-12 pt, first-line indent 0.20 inch, compact 0.95 line spacing, 6 pt after.
- Heading 1: numbered, uppercase/small-caps visual treatment, bold, approximately 11-12 pt, 8 pt before and 4 pt after, keep with next.
- Heading 2: numbered, italic, approximately 11-12 pt, 6 pt before and 3 pt after, keep with next.
- References: 8 pt, compact spacing.
- All title and heading text remains black. No decorative rule is added under the document title.

## Lists and tables

- Reuse the template's numbering definitions for ordered headings and bullets where practical.
- Report tables use visible light-gray borders, a dark blue header with white text, pale-blue alternating rows, vertically centered cells, and content-weighted column widths.
- Table captions appear immediately above their tables and are kept with the following table. Table header rows repeat across page breaks.
- Figures are centered, limited to the text width, and followed by a centered caption.

## Components and content flow

1. Cover with project title, PBL submission statement, degree, interdisciplinary course areas, known contributor, institutional logo, institution, location, and current month/year.
2. Abstract.
3. List of Figures.
4. List of Tables.
5. Table of Contents.
6. Numbered main report: Introduction; System Design and Methodology; Implementation; Experiments and Validation; Results; Deployment and Operations; Conclusion and Future Work; References.

## Slot map

- Cover paragraphs 1-23: rewrite all textual placeholders; preserve the institutional logo drawing in paragraph 18; remove unknown faculty, roll number, department, and course-code placeholders rather than inventing them.
- Front-matter headings 24-28: retain the five source roles, expand them with real abstract/list/contents material, and preserve their one-section-per-purpose organization.
- Paragraphs 29 onward: remove all sample skin-cancer content and sample images 2-5; replace with the project report using the source's title, heading, body, caption, and references patterns.
- No source text is preserved merely because it is present: the sample domain content and template-writing instructions are not project content.

## Text coverage and stable locators

- Editable body is `word/document.xml`; there are no content controls or fields in the source.
- The institutional logo is `word/media/image1.png`, referenced by the drawing in body paragraph 18, and is preserve-only.
- Sample images `word/media/image2.png` through `image5.png` are removable edit slots.
- Headers and footers are empty and remain structurally present.

## Package preservation

- Baseline package contains 23 parts. Preserve styles, numbering, theme, font table, footnotes, endnotes, footer, settings, web settings, custom XML, and core relationships unless a targeted final-field update requires a settings change.
- Preserve `word/media/image1.png` byte-for-byte.
- Body XML and its relationships are editable for content, new figures, tables, captions, bookmarks, and navigation fields.
- Remove only sample image relationships that become unused.

## Fidelity gates

- The source file must retain the recorded SHA-256.
- The final cover must remain recognizably derived from the template and keep the institutional logo.
- A4 geometry, black Times typography, numbered academic structure, justified body text, and front-matter ordering must remain consistent.
- Every page must render without clipping, overlap, orphaned headings, broken tables, or unexplained blank pages.
- All template placeholders, sample skin-cancer material, and authoring notes must be absent.
- Every listed figure and table must exist and have a matching caption.
