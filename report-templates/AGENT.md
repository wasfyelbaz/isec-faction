# AGENT.md — Converting an iSec report template for OWASP Faction 2

Read this before touching any report template. It is the record of everything learned while
converting three templates (the Web Application Penetration Testing template, "WAPT", now at v9,
the Mobile Application template, "MAPT", in the fork, and the Internal/External Network template, "INT_EXTNWPT",
now at v5, converted in one scripted pass with `tools/template-edits/build_network_v1.py` by following this guide
and refined by `build_network_v2.py` … `build_network_v5.py`) so that the next template
(Network, Wi-Fi, Internal, External, or any other assessment type) is converted the same way,
without rediscovering the engine's behaviour.

Everything below was verified on the fork `wasfyelbaz/isec-faction` at commit `b77a81b` or
later, on a real generation in the local Faction build, in both Microsoft Word and Faction's own
PDF output. Where something was not verified, the text says so.

Companion files in this folder:

| File | Use it for |
|---|---|
| `Faction2_WAPT_Template_Mapping.md` | The full built-in variable reference (section 2) and the Web template's section-by-section mapping. Read section 1 (engine rules) and section 2 before anything else. |
| `Faction_Backend_Changes_Needed.md` | What Faction cannot do yet (gaps), the UDF set with options and defaults (section 2), engine fixes made and pending (section 4), the unified UDF names (section 5). |
| `Faction_UDF_Definitions.json`, `Import-FactionUdfs.ps1` | The Web template's 21 UDFs in the API's shape and the script that imports them. Copy and adapt for a new template. |
| `Faction_WAPT_Template.css` | The template stylesheet with the engine-compatibility rules explained in comments. Reuse as the starting CSS. |
| `iSec_WAPT_Template_Faction.docx` / `_UPLOAD.docx` | The finished Web template: annotated master with Word comments, and the comment-free copy that is uploaded. The reference implementation for every recipe in this file. |
| `samples/` | Generated reports (DOCX + Faction PDF) at each template version, for visual comparison. |
| `tools/` | The scripts used to edit, test and verify templates (section 20). |

---

## 0. Standing rules

1. **The template is the product.** Fix layout in the template whenever possible. A change to
   the Faction source (`backend/src/main`, `frontend/src`) needs an explicit go from the
   developer, every time, with what / why / blast radius / alternative stated first
   (`CLAUDE.md` in the fork). Commits and pushes are separate permissions.
2. **Two files per template.** An annotated master (`<Name>_Faction.docx`, Word comments by
   "Faction Mapping" at every gap) and an upload copy (`<Name>_Faction_UPLOAD.docx`, no
   comments). Faction copies Word comments into every generated report, so never upload the
   master.
3. **Never fake a mapping.** If Faction has no variable for something, leave it static or make it
   a UDF and record the gap. Do not use a look-alike variable (`${impact}` is a rating, not the
   impact narrative; `${asmtAppid}` is an ID, not the application name).
4. **Every change is verified in both renderers**: the DOCX opened in Word, and the PDF that
   Faction produces (LibreOffice in the backend container). They disagree in specific ways
   (section 13). Something that looks right in Word is not done.
5. **Keep the assessment-specific content.** Only the integration mechanics are copied from the
   Web template; methodology, checklists, section wording and the findings layout belong to the
   assessment type (section 16).
6. **Version the template** (v1, v2 …), keep the previous upload copy in `samples/`, and write
   what changed into the mapping document's history list. Scripts that transform a template go
   into `tools/template-edits/` with the version they produce.

---

## 1. How Faction fills a template

The engine is `backend/.../util/reporting/DocxUtils.java` (docx4j), driven by
`DocxReportGenerationService`. Official docs: https://docs.factionsecurity.com/reporting/docx-templates/

**Processing order** (matters for what can contain what): report-section wrappers →
`${vulnTable}` tables → `${fiBegin}`…`${fiEnd}` blocks → `${summary1}`/`${summary2}` →
assessment variables, dates, UDFs → client package (name, contacts, images) → `${pageBreak}` →
extension placeholders → `${TOC}`. Then the DOCX is **round-tripped through LibreOffice**
(TOC refresh, normalisation) and the PDF is produced by LibreOffice from that DOCX. The DOCX
the user downloads is the LibreOffice-saved one, not the docx4j output.

**Engine rules that shaped every decision:**

1. A tag is `${name}` and must be one uninterrupted run of text in Word. Faction merges split
   runs, but spell-check marks, autocorrect or a formatting change mid-tag still break it. Type
   tags with spell-check off, in one go, with one formatting.
2. Inline tags are replaced wherever they sit: sentences, table cells, cover text boxes.
3. Block tags (rich text, lists, TOC, page break, images) must be **alone in their paragraph**;
   the whole paragraph is replaced. Inside a table cell the tag must be the only text of the cell.
4. Findings render in the assessment's display order, never re-sorted by severity.
5. `${severity}` and the keys of `${color}` / `${cells}` / `${fill}` are the labels from
   Admin → Terminology (`Critical`, `High`, `Medium`, `Low`, `Informational`). "Info" is not a
   label unless renamed there.
6. Report sections (`${vulnTable S}`, `${fiBegin S}`, `${if-section S}`) work in this fork
   (`CommunityEditionPolicy.enabled()` returns true). A finding filed under a section the template
   has no block for is dropped silently; keep a bare `${fiBegin}` block as the fallback.
7. Headers and footers receive: simple assessment variables, STRING/DROPDOWN UDFs, and
   `${clientImage}`. Not dates, not rich text, not lists. The text substitution only covers the
   headers/footers of the last section's policy; `${clientImage}` covers every header and footer
   part of every section.
8. Rich text (descriptions, recommendations, details, RICH_TEXT UDFs) is HTML converted with the
   template CSS. Images inside it are capped at 600 px wide.
9. An unresolved `${…}` alone in a paragraph is offered to App Store extensions; otherwise it
   stays in the report verbatim. Your validation must grep for `${` in the output.
10. Word charts, SmartArt and embedded Excel pass through untouched, except a chart preceded by a
    `${chartData …}` marker paragraph: that chart's cached values and its embedded workbook are
    rewritten from report data (section 3).
11. Word comments in the template are copied into every generated report.
12. The template DOCX upload (`POST /api/v1/report-templates/{id}/file`) fails with 400 unless
    the multipart part carries the DOCX MIME type
    (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`).
13. The engine closes a cell itself: when rich text placed into a table cell ends with a table, it
    appends an empty paragraph, because a `w:tc` whose last child is a `w:tbl` is invalid and makes
    Word and LibreOffice unwrap the outer table. A value written through the API no longer has to
    end with `<p></p>`.

---

## 2. Preparing the Word template

Work on a copy of the original iSec template. In this order:

1. **Remove what Faction cannot condition away**: the "Notes For Penetration Testers" page (no
   conditional page removal exists), the four duplicate placeholder rows in the findings summary
   table, the duplicate "Finding 2…5" blocks. Keep exactly one of each repeating structure.
2. **Replace `{…}` placeholders with `${…}` tags** (single braces are ignored). One run, one
   formatting, spell-check off. Keep the placeholder's own paragraph formatting.
3. **Keep the native Word TOC.** Do not add `${TOC}` (it would only give levels 1–3 and break the
   template's level-4 "Proof Of Concept" entries). Faction's LibreOffice pass refreshes it.
4. **Remove all yellow highlighting**, including on whitespace, tabs and paragraph marks
   (`w:highlight` in runs and paragraph-mark run properties). Placeholder highlights survive into
   the report otherwise. `tools/template-edits/cover_fix.py` strips every `w:highlight` in the
   package.
5. **Give tables that use `TableGridLight` explicit borders** (single, 0.5 pt, `BFBFBF`) written
   directly into `tblPr`, and complete the undeclared edges of partially bordered merged cells.
   Reason: the LibreOffice round trip writes an empty `<w:tcBorders/>` on every cell of a
   `TableGridLight` table, and Word then draws no grid at all. The PDF is unaffected, the DOCX is
   broken. **Keep the style name**: restyling to `TableGrid` also survives the round trip but
   draws the lines black, which does not match the iSec reports (see lesson 7 and section 13).
   `tools/template-edits/table_fix.py` does the border part (it marks touched tables with
   `data-was-light`); `build_network_v5.py` is the worked example that keeps the light line. The
   MAPT template got the earlier, black-lined cure (fork commit `643ec3e`).
6. **Fix cover text boxes that clip**: the date box in the Web template clipped "September 18,"
   because its DrawingML extent was too narrow. Widen the box (`wp:extent` and the VML fallback
   `style` width), left-align the paragraph, and set the insets so text clears the decorative
   shapes. See `cover_fix.py` for the exact edits.
7. **Set the template's Report Font** in the Report Designer to the family the template uses in
   Word (Calibri for iSec). Section 12 explains why.
8. **Upload the CSS** (`Faction_WAPT_Template.css`) in the Report Designer, then the UDFs
   (section 4), then the DOCX.

What stays unchanged: page size and margins, the section breaks, the header/footer structure,
the Word numbering definitions (they number the findings), the styles, all static text.

**Work on the XML, not only in Word.** Every structural change we made (text box geometry,
borders, highlights, footer boxes) was scripted against the package XML with Python's
`zipfile` + `re`/`lxml`, so it is repeatable and version-controlled. Word's UI is used to
inspect and to type tags, scripts are used to transform. After a script, always open the result
in Word once (a broken XML shows "unreadable content").

---

## 3. Built-in variables

The complete list with rules is `Faction2_WAPT_Template_Mapping.md`, section 2. The subset every
iSec template uses:

| Need | Tag | Placement rule |
|---|---|---|
| Assessment name, type | `${asmtName}`, `${asmtType}` | Inline anywhere. |
| Application ID | `${asmtAppid}` (lower-case `id` in the DOCX engine) | Inline. It is the configured App ID, not the name. |
| Assessors | `${asmtAssessor}` (first), `${asmtAssessors_Comma}`, `_Lines`, `_Bullets` | First name inline; list forms are block tags (alone in paragraph or cell). |
| Dates | `${today MMMM d, yyyy}`, `${asmtStart dd/MM/yyyy}`, `${asmtEnd dd/MM/yyyy}` | Body only. Java `SimpleDateFormat` pattern after a space. `${asmtEnd}` is the planned end. No ordinal suffix exists. |
| Counts | `${riskCount9}` … `${riskCount5}` (Critical → Informational), `${riskTotal}` | Inline, headers and footers too. |
| Client | `${asmtClient}`, `${asmtClient_<field>}` | Inline. Client = the assessment's organization. |
| Distribution list | `${clientContactTable}` + `${loop}` row with `${contactName}`, `${contactTitle}`, `${contactEmail}`; or `${clientContacts_Lines / _Bullets / _Comma}` | Table form: config tag in a merged row above the loop row. |
| Client images | `${clientImage <slot> width=W height=H}` | Alone in its paragraph; body, cells, text boxes, headers, footers. Section 6. |
| Per finding | `${vulnName}`, `${severity}`, `${cvssScore}`, `${cvssString link}` or `${cvssLink <label>}` (inside a hyperlink), `${assetLocation}`, `${category}`, `${desc}`, `${rec}`, `${details}`, `${count}` (table rows only), `${likelihood}` / `${impact}` (ratings) | Only inside a `${vulnTable}` row or a `${fiBegin}` block. |
| Structure | `${pageBreak}`, `${if-section S}` … `${end-section S}` | Alone in top-level body paragraphs. |
| Native charts | `${chartData severity}`, `${chartData checklist}` | Alone in the top-level body paragraph directly before the chart it feeds. The marker paragraph is deleted on generation. |

Rules of thumb:

- Prefer a built-in whenever the data exists on the assessment, the client or the finding. The
  report then needs no typing and cannot drift.
- Do not use `${asmtTeam}` (always empty) or `${asmtAccessKey}` (legacy alias).
- `${cvssString link}` replaces the hyperlink text with the vector. To keep a label instead, write
  `${cvssScore} (${cvssLink View CVSS Metrics})`: the label stays as the link text and the link
  target becomes the NVD calculator for that finding's own vector (v3 or v4 by the template's
  scoring type; a `CVSS:3.1/` prefix on the vector wins). Each finding gets a fresh relationship,
  so cloned blocks never share a URL. `${cvssLink}` without a label prints the vector, exactly as
  `${cvssString link}` does. Closes the old gap 5.10.
- `${assetLocation}` is one string; several URLs do not split into lines (gap 5.11).
- `${count}` is not resolved inside `${fiBegin}` blocks; number finding headings with Word
  multilevel numbering instead (the Web template uses `5.1.%1`).
- `${noIssuesText …}` belongs to the summary table only; inside a findings block it prints once
  per finding.

**Native charts (`${chartData severity}`, `${chartData checklist}`)**: the marker must be the whole
text of a top-level body paragraph placed directly before the chart it feeds, and it is deleted
during generation, so it costs no space. The engine writes both the chart's cached values and the
cells of its embedded workbook, so the picture and the data behind it stay in step. It only finds
charts at body level; a chart inside a table cell is never reached. `severity` writes the
per-severity finding counts. `checklist` writes the PASS / FAIL / NA totals summed across the
checklists attached to the assessment (section 19 has the three API calls that attach one); with no
checklist attached the counts are zero and the chart draws empty on purpose, rather than keeping the
template's placeholder numbers, which would read as real results.

---

## 4. User-defined fields (UDFs)

**When**: the value is typed per engagement or per finding and Faction has no built-in for it
(project name, version, environment, test type, reviewers, narrative impact, in/out-of-scope
functions, credentials, limitations, recommendations). Decision rule: built-in if the data lives on
the assessment/client/finding record and a variable exposes it; UDF if a person has to write it;
static if it is the same in every report; gap (documented) if neither is possible.

**How they are created**: Report Designer → template → "User Defined Fields for Assessments" /
"… for Vulnerabilities", or by API: `PUT /api/v1/report-templates/{id}` with
`{"userDefinedFields":[…]}` (the whole list; keep existing `id`s by `variableName` so saved values
survive, see `Import-FactionUdfs.ps1` and `tools/faction/e2e_client.py` setup phase).
Fields: `variableName` (snake_case, unique per template), `displayName`, `helpText`, `fieldType`
(`STRING`, `RICH_TEXT`, `DROPDOWN`), `fieldScope` (`ASSESSMENT`, `VULNERABILITY`),
`dropdownOptions`, `defaultValue`, `displayOrder`, `required`.

**How they are referenced**: `${variable_name}`. STRING/DROPDOWN inline anywhere, headers and
footers included; RICH_TEXT as a block tag (alone in its paragraph or cell), rendered with the
template CSS. Vulnerability UDFs only inside a `${vulnTable}` row or `${fiBegin}` block.
`${variable_name link}` inside a Word hyperlink sets the link target from the value.

**How values are populated**: assessment UDFs on the assessment page ("Variables" tab);
vulnerability UDFs on each finding's form. `defaultValue` pre-fills new assessments.

**Naming conventions (already unified between Web and MAPT, keep them):**

| Information | Variable |
|---|---|
| Project / application name | `project_name` |
| Report version | `report_version` |
| Initial test / retest | `asmt_phase` (DROPDOWN) |
| Black / Grey / White box | `test_type` |
| Working hours clause | `testing_hours` (each option holds the full sentence) |
| URL, version, environment | `app_url`, `app_version`, `environment` |
| Reviewers, approver | `first_reviewer`, `second_reviewer`, `approver` |
| Scope, credentials, limitations, recommendations | `in_scope_functions`, `out_of_scope_functions`, `provided_credentials`, `limitations`, `recommendations` (RICH_TEXT) |
| Per finding | `affected_user`, `isec_checklist_ref`, `impact_narrative` (RICH_TEXT), `owasp_top_ten` (only when `${category}` is not the OWASP list) |
| Executive summary, scope (Faction defaults) | `summary1`, `summary2` |

A new template reuses these names for the same information and adds only what is specific to
its assessment type (for example MAPT's `app_variant`). Renaming a variable on a live template
disconnects the values already entered. Keep one `Faction_UDF_Definitions.json` per template.

---

## 5. Images

**Kinds of image in an iSec template and where they live:**

| Image | Where | How it is placed | Faction behaviour |
|---|---|---|---|
| Cover artwork (full page) | Body, cover section | Anchored picture, behind text | Untouched. |
| Page background band (iSec wordmark, shapes, lines) | Header of the body sections, full-page picture | Anchored at page position, behind text | Untouched; repeats on every page of the section. |
| Static logos, icons | Body or header | Inline or anchored | Untouched. |
| Client logo | Body text box (cover) and footer text box | `${clientImage}` tag replaces a paragraph | Engine inserts a native picture registered on that part. |
| Screenshots in findings | Rich-text content (editor) | Not in the template | Embedded at generation, capped at 600 px. |

**Rules that survive the LibreOffice round trip** (verified): inline pictures inside text boxes;
text boxes anchored to a paragraph or to the page (DrawingML `wps` with a VML fallback);
page-anchored full-page pictures in headers. **What does not survive**: Word frames (`w:framePr`),
which LibreOffice drops (the footer page number lived in one and vanished); Roman page-number
format on the second section; `_Toc` bookmarks (section 13).

**Sizing**: the engine works in CSS pixels at 96 dpi: `1 cm = 37.8 px`, `1 px = 9525 EMU`.
Measure the placeholder in the template (either the picture's `wp:extent` in the XML or the
rendered PDF with PyMuPDF) and convert.

**Anchoring rule for dynamic images**: put the tag in a text box whose position is absolute (page
or column relative) and whose size equals the printed image plus the box insets. The engine
centres the picture in the paragraph it replaces (the paragraph keeps its own alignment, but a
box that is exactly one image wide makes alignment moot), so a box narrowed to the image's
footprint pins the image where the placeholder was.

---

## 6. Client logo

**Source**: Clients → edit → Client images. Each image has a slot name (`logo` by convention;
others such as `cover` or `signature` are possible). PNG, JPEG, GIF, WebP, SVG up to 5 MB. SVG
cannot be rasterised by the engine and is only usable through the HTML route in the body; use
PNG for logos. The assessment must belong to the client: the application is attached to the client,
and the assessment records the client when it is created. An assessment created before its
application had a client keeps "no client" (the report prints no logo, no name, no contacts); the
UI shows the application's client as a display fallback, the report does not.

**Tag**: `${clientImage <slot> width=<px> height=<px>}`, alone in its paragraph.

| Form | Result |
|---|---|
| no size | natural size at 96 dpi, capped at the page width (15.9 cm) |
| `width=` or `height=` | scaled proportionally |
| `width=` and `height=` | fitted into that box, padded transparently, centred, empty margins of the file trimmed first: **every client's logo occupies the same box whatever its shape** |

The engine trims transparent borders and borders in the flat colour of the file's corners
(white backgrounds), so a logo file with generous padding still fills the box. Rasterised at 4×
for sharpness. A slot the client has not filled removes the paragraph (or empties it if it is the
only paragraph of a cell or text box). The same slot may appear any number of times, and each
header or footer part gets its own copy of the picture.

**Cover recipe (Web v6→v9, reuse as is):**

1. The original placeholder is a picture "[Insert Image Here]" inline in a text box at the top
   right. Note its printed size (3.67 × 1.44 cm) and position.
2. Replace the picture paragraph with the tag `${clientImage logo width=139 height=54}`
   (139 × 54 px = 3.67 × 1.44 cm), paragraph centred.
3. Narrow the text box to the image plus insets (4.29 cm) and move it so the image's right edge
   stays where the placeholder's was (`build_v6.py` computes this from the placeholder's
   geometry). Update both the DrawingML box and its VML fallback.

**Footer recipe (Web v8→v9):**

1. Footers are separate parts (`footer2.xml`, `footer4.xml` in the Web template: the default
   footer of the front matter and the first-page footer of the body section). Put the tag in
   **each** footer that shows the band.
2. Use a small text box anchored to the footer paragraph, positioned right after the separator bar,
   holding `${clientImage logo width=64 height=25}` (1.69 × 0.66 cm, the iSec wordmark's own
   width, vertically centred on it). `build_v8.py` restores the placeholder box from the original
   template; `build_v9.py` sets the final geometry from measurements of the rendered band.
3. Do not expect the footer logo to match the wordmark's height and width at once: a wide
   wordmark logo fills the width, a square logo fills the height. That is the intended behaviour
   of the box.

**What is controlled where**: the template decides the slot name, box size and position; Faction
decides which file (the client record) and does the fitting. The old per-client approach
(`Set-ClientLogo.py`, swapping picture bytes and uploading one template per client) is superseded
and must not be used for new templates.

---

## 7. Header and footer structure

- **Header**: the full-page background picture (band with the iSec wordmark, shapes and rule at
  the page bottom is part of this picture), anchored to the page, behind text. Sections have
  their own header parts; the cover section uses a "first page" header without the band.
- **Footer**: the page number and the client logo. Both are text boxes anchored at absolute
  positions, because plain footer paragraphs cannot reach the pink tab or the band and Word
  frames do not survive LibreOffice.
- **Page number**: text box anchored to the page at the tab's coordinates (Web v7: 0.54 / 28.71 cm,
  1.10 × 0.80 cm, insets 0, vertical anchor centre) containing the PAGE field, white, centred.
  `build_v7.py` converts a frame-based number into this. The Roman numerals of the front matter
  (`pgNumType upperRoman` on section 2) are lost in Faction's output (prints 1, 2, 3); not fixed.
- **Dynamic content allowed in footers**: `${clientImage}`, simple assessment variables and
  STRING/DROPDOWN UDFs (last section's headers and footers only for text). No dates.
- **Static iSec branding stays in the background picture**; client-specific elements are always
  tags in text boxes on top of it. Never bake a client logo into the picture.
- Sections: the Web template has three (cover, front matter, body) with `titlePg` first-page
  headers/footers. LibreOffice re-numbers and re-links them but keeps their content.

---

## 8. Cover page

| Element | Treatment |
|---|---|
| Artwork, iSec logo, shapes | Static. |
| Title text box `{Client} {Project} Penetration Testing Report` | `${asmtClient} ${project_name} Penetration Testing Report` (inline in the text box; keep the box's formatting). |
| Assessment type line | Static text or `${asmtType}`. |
| Date text box | `${today MMMM d, yyyy}`. Widen the box so the longest month fits (Web: 2.5 in, left-aligned, 15 pt left inset so it clears the corner shape). |
| Client logo | `${clientImage logo width=139 height=54}` in the narrowed placeholder box (section 6). |
| Highlights | Remove all. |

Dates are replaced only in the body; the cover is body, so they work there. Text boxes are
inside `mc:AlternateContent` with a DrawingML choice and a VML fallback: edit both.

---

## 9. Table of findings (summary table)

Layout that works (Web 2.9, MAPT the same):

| Row | Content |
|---|---|
| Header row | Column titles, static. |
| One merged cell across all columns, three paragraphs | `${vulnTable}` · `${cells Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0,…}` · `${noIssuesText No vulnerabilities were identified during this assessment.}` |
| `${loop}` row | `${loop}` (or `${loop}${count}`) · `${vulnName}` · `${severity}` with cell fill `FAC701` · `${cvssScore}` · `${assetLocation}` · `${affected_user}` · `${asmt_phase}` with a static grey fill |

Rules:

1. **All config tags share one merged row above the loop row.** The engine removes exactly one
   `${…}` row; a second config row, or a config row below the loop row, prints in the report.
2. One loop row only; delete the other placeholder rows. The row repeats once per finding, keeping
   its formatting, borders and the column auto-numbering.
3. Sentinel colours: `FAC701` text or fill on the loop row becomes the severity colour from the
   map; `FAC702` likelihood, `FAC703` impact.
4. Map keys must equal the Terminology labels. The template said "Info", Faction says
   "Informational": either rename in Admin → Terminology or key the map on "Informational".
5. Many findings: the table grows across pages; keep the header row set to "repeat as header row"
   in Word. Few or zero findings: zero findings prints the `${noIssuesText}` wording in one row.
6. Status column for an initial-test template: `${asmt_phase}` with a static grey fill; the
   Fixed / Not Fixed keys in the map are harmless and kept for the retest template.
7. For a table split per section (per target/host), use `${vulnTable SectionName}` per table.

---

## 10. Individual finding sections

The block, top-level paragraphs in order (Web 6.3, copy the shape):

```
${fiBegin}                                   ← alone, top-level paragraph
${fill Critical=C00000,High=FFC000,Medium=FFFF00,Low=00B050,Informational=00B0F0,…}
Heading 3: ${vulnName}                       ← Word numbering supplies 5.1.N
[table]  Severity ${severity} (fill FAC701) | CVSS ${cvssScore} + hyperlink ${cvssString link} | Status ${asmt_phase}
         Affected Assets ${assetLocation}
         OWASP Top Ten ${category}   (or a DROPDOWN UDF when categories are not OWASP)
         iSec Check List ${isec_checklist_ref}
         Description ${desc}          ← only text in the cell
         Impact ${impact_narrative}   ← RICH_TEXT UDF, only text in the cell
         Recommendation ${rec}        ← only text in the cell
Heading 4: Proof Of Concept
${details}                                   ← screenshots and steps come from the editor
${pageBreak}                                 ← last paragraph: one page per finding
${fiEnd}
```

- Config paragraphs inside the block (`${fill}`, `${color}`, `${custom-fields}`) are top-level
  paragraphs; a config tag inside a table drops the whole table.
- `${fill}` is the block's map (the table uses `${cells}`).
- Screenshots, evidence and references are written in the finding's rich-text fields in the
  editor; `${Figure#.N}` in that content becomes "Figure <finding number>.N".
- Word heading numbering on the `${vulnName}` heading is preserved per copy, so findings number
  themselves.
- The engine advances its insertion point by the size of the expanded rich text (fixed in the
  fork), so long Proofs of Concept with tables no longer swallow the next finding.
- Per-target or per-host grouping: one `${if-section S}` / `${fiBegin S}` block per section, plus
  a bare block as fallback, as the MAPT template does for Android / iOS / API.

---

## 11. Loops and conditional logic

| Construct | Where | Notes |
|---|---|---|
| `${loop}` / `${loop-N}` | First cell paragraph of the repeating row of a `${vulnTable}` or `${clientContactTable}` | `loop-N` repeats N extra rows. |
| `${fiBegin}` … `${fiEnd}` | Top-level body paragraphs | Everything between is copied per finding. Not inside tables or text boxes. |
| `${if-section S}` … `${end-section S}` | Top-level body paragraphs | Removes the wrapped content when section S has no findings. |
| `${pageBreak}` | Top-level paragraph, or last paragraph of a block | Inside a block: one page per finding. |
| `${faction-bar-chart}`, `${checklist-<name> columns=[…]}` | Alone in a paragraph | Need the App Store extension installed; not used by the iSec templates (layout differs). |

There is no `if-eq` / `if-set` on values; alternative sentences live in DROPDOWN option text
(`testing_hours`). There is no evidence or screenshot loop in the DOCX: those live in rich text.

**Common mistakes that break generation or output** (all seen):

- A tag split into runs by spell-check or a mid-tag formatting change → stays literal.
- A block tag sharing its paragraph with other text → stays literal.
- Two config rows in the summary table, or the config row below the loop row → one prints.
- `${noIssuesText}` inside a findings block → printed per finding.
- `${count}` in a findings block → literal.
- A date tag in a footer → literal.
- Word comments left in the upload copy → appear in every report.
- A finding filed under a section without a block → silently missing.

---

## 12. Report fonts

- The DOCX carries font **names**. The template's static text keeps the fonts set in Word. Text
  that Faction generates (findings, rich text, tables it builds) uses the template's **Report
  Font** (Report Designer → Custom CSS Formatting): the engine sets it as the body font and maps
  the stock CSS's `Arial` onto it. Set Report Font to the family the template uses (Calibri).
- The PDF is drawn by LibreOffice in the backend container with the fonts installed there. A
  missing family is substituted silently (Calibri → Carlito automatically; unknown → DejaVu Sans),
  which changes metrics and pagination. The backend image now ships Calibri (regular, bold, italic,
  light), Carlito, Liberation and DejaVu.
- **PDF Fonts** (same panel) uploads TTF/OTF files, one per style; the server installs them,
  refreshes fontconfig and restarts LibreOffice. "Show the N font families available on the
  server" lists what the PDF can use. A warning appears when the Report Font is not installed.
- Rule: Report Font = the template's family, and that family must be in the server list. Check the
  PDF's fonts (`pdffonts`, or PyMuPDF `page.get_fonts()`) during validation; substituted fonts
  show as Carlito/DejaVu/Liberation.
- Metric-compatible substitutes keep line breaks (Carlito for Calibri, Liberation Sans for
  Arial); anything else shifts page breaks and the TOC.

---

## 13. Word versus LibreOffice (Faction's renderer)

Verified differences; each has a template-side cure or is documented as accepted:

| Area | What happens in Faction's output | Cure |
|---|---|---|
| Table borders | `TableGridLight` tables come back with empty `tcBorders` on every cell; Word shows no grid, the PDF is fine | Restyle to `TableGrid` with explicit cell borders (section 2). |
| Word frames (`framePr`) | Dropped; the framed paragraph becomes a normal one (footer page number vanished) | Text box anchored to the page. |
| TOC bookmarks | `_Toc` bookmarks are dropped; the DOCX opened in Word shows "Error! Bookmark not defined." until fields are updated (F9); the Faction PDF has a correct TOC | Accepted for now. |
| Page number format | Roman numerals of the second section become decimal | Accepted for now. |
| Fonts | Only container fonts; substitution shifts layout | Section 12. |
| Text boxes | Survive (paragraph- and page-anchored, choice + fallback) | Use them for anything positioned. |
| Inline pictures in text boxes and footers | Survive | Used for logos. |
| Section headers/footers | Re-linked and renumbered but content kept | None needed. |
| Empty cells created by the engine | The "no contacts" row and HTML tables inside rich text come without borders | Engine-side, minor, open. |
| Heading numbering | Kept | None. |
| Cover text box clipping | Word wraps, LibreOffice may clip a box that is too narrow | Size boxes for the longest value. |

Whenever something looks right in Word and wrong in the PDF (or the reverse), inspect the
LibreOffice-saved DOCX's XML: the difference is always visible there.

---

## 14. Page layout and page breaks

- A4 portrait, the template's margins (Web: 1.27 cm left), three sections. Do not change page
  setup; the background pictures are sized to it.
- One finding per page: `${pageBreak}` as the last paragraph of the findings block. Do not add a
  manual page break after `${fiEnd}` too, or every report ends with a blank page.
- Keep headings with their table: "keep with next" on the finding heading and the first rows.
- Summary table: header row repeats; rows may break across pages unless "allow row to break
  across pages" is off for the loop row.
- Blank pages come from: a page break followed by a section break on a new page, `${pageBreak}`
  plus a manual break, or an empty paragraph after a full-page table. Check the page count of the
  generated PDF against expectations (Web: 21–30 pages with 3 findings depending on content).
- The front-matter/body section change is where LibreOffice loses the Roman numbering; keep the
  section structure anyway, the headers depend on it.

---

## 15. Static versus dynamic content

| Static in the DOCX | Dynamic through Faction |
|---|---|
| Methodology text, risk criteria, disclaimer wording, report organisation, appendices | Client name, contacts, logo (client record) |
| iSec branding, background pictures, shapes | Assessment name, type, dates, assessors (built-ins) |
| Column headers, section headings, standard bullets | Project, version, environment, scope, credentials, limitations, recommendations, reviewers (UDFs) |
| Checklist tables and charts (gaps, filled by hand) | Findings: names, severity, CVSS, assets, category, description, impact, recommendation, proof of concept with screenshots (built-ins + finding UDFs) |
| Placeholder fills (Status grey) | Counts per severity, totals |

When in doubt: if two reports for two clients would differ, it is dynamic; if it only changes when
iSec changes its methodology, it is static.

---

## 16. Template-specific content

A Network, Wi-Fi, Internal, External or Mobile template keeps its own sections. Do not paste the
Web template's sections over it. The mechanics are the same; the content maps differently:

- **Scope**: hosts, ranges, SSIDs or applications instead of URLs. Use report sections per target
  when findings must be grouped (`${vulnTable S}` + `${fiBegin S}` per section, findings filed
  under sections in Faction). Where a table of scope items is needed, use a RICH_TEXT UDF with a
  table, until `${engagementUrlTable}` or similar exists (gap 3.3).
- **Category lists**: `${category}` prints the vulnerability category list configured in Faction.
  It is the OWASP Top 10 for Web; for other types either configure the matching taxonomy or use a
  DROPDOWN UDF (as MAPT does with `owasp_category`).
- **Checklists and charts**: still manual (gaps 5.5–5.7); keep the tables static and fill by hand,
  note it in the mapping document.
- **Assessment type**: the template is bound to an assessment type in the Report Designer; create
  the type first (Admin → Assessment Config) so findings, sections and UDFs apply to it.
- **Naming**: reuse the shared UDF names (section 4) for shared information; add
  type-specific names with the same style (`snake_case`, one concept per field).
- Write a mapping document like `Faction2_WAPT_Template_Mapping.md` for the new template:
  sections 3 (built-ins), 4 (UDFs), 5 (gaps), 6 (layout of the dynamic blocks), 7 (changes and
  history). It is the deliverable that makes the template maintainable.

---

## 17. Lessons learned (problem → cause → what worked)

1. **Findings table printed a config row** → the engine removes one `${…}` row only → one merged
   config row above the loop row.
2. **"No issues" text repeated under every finding** → `${noIssuesText}` inside the block → keep it
   in the summary table only.
3. **`${asmtAppId}` stayed literal** → engine keyword is `asmtAppid` → write it lower-case.
4. **Template upload returned 400** → multipart part without the DOCX MIME type → set it.
5. **Cover date clipped ("September 18,")** → text box too narrow, centred text touching the
   corner shape → widen, left-align, 15 pt inset.
6. **Yellow highlight in reports** → placeholder highlights on runs and paragraph marks → strip
   every `w:highlight`.
7. **No table grid in the downloaded DOCX** → LibreOffice writes empty `tcBorders` for
   `TableGridLight` → keep the style **and** write the same borders directly into `tblPr`
   (`single`, `sz=4`, `color=BFBFBF`). Restyling to `TableGrid` also works but draws the lines
   black, which does not match the iSec reports; direct borders keep the light grey line.
8. **Client missing from the report although the application belonged to the client** → the
   assessment copies the client at creation and a later move does not update it (a fallback was
   built and reverted on request) → create the assessment after attaching the application, or
   create a new one.
9. **Client logo printed under "1.0 Document Control" instead of the design's spots** → the tag was
   placed as a body paragraph → cover text box and footer boxes (v6–v9).
10. **Footer logo impossible** → the engine only scanned the body and its HTML route registered
    images on the main part → engine change (fork `b77a81b`): every header/footer part scanned,
    native picture on the owning part.
11. **Logos of different shapes came out different sizes / the OneBank logo looked tiny** → width-only
    sizing and padded PNG files → `width= height=` box with margin trimming, box sized to the
    placeholder (cover) and to the iSec wordmark (footer).
12. **CSS cannot fit an image into a box** → `max-height` is ignored, `height` distorts, inline
    images vanish → do sizing in the engine, not CSS.
13. **Footer page number vanished** → Word frame dropped by LibreOffice → page-anchored text box.
14. **PDF fonts were DejaVu** → no Calibri in the container → fonts bundled + PDF Fonts upload.
15. **Word shows "Error! Bookmark not defined." in the TOC** → `_Toc` bookmarks dropped → accepted
    (PDF is correct; F9 in Word fixes the DOCX).
16. **Merge conflict in `DocxUtils`** → both sides fixed the findings-order bug → keep upstream's
    version, drop the local one, keep both test classes.
17. **A parallel agent session stashed our uncommitted engine change** ("old experiment") →
    never share a worktree between sessions; recover from the stash by SHA, never `git stash pop`.
18. **WSL had no internet (Docker could not pull base images)** → stale ICS NAT with VPN clients
    (FortiClient, ExpressVPN) present; mirrored networking is refused on that machine → close the
    VPN clients, `wsl --shutdown; Restart-Service SharedAccess`; meanwhile an offline compile can
    be patched into the running jar to keep testing.
19. **A table typed into a RICH_TEXT value that sits inside a Word table cell flattened the outer table**
    (Network 3.1.1 / 3.1.2: the dark header rows became plain paragraphs, only the typed table kept a grid) →
    the cell's last child was the typed `w:tbl`, which is invalid OOXML → tell testers to type one line per
    item in those cells; keep tables in rich text only where the tag stands in a body paragraph (Proof Of
    Concept). The engine now appends the closing paragraph itself (section 1 rule 13), but lesson 22 stands:
    give each column its own cell rather than nesting.
20. **Assessment creation failed with "Unknown field ID: report_version"** → `initialFieldValues` (assessments)
    and `fieldValues` (vulnerabilities) are keyed by the UDF's **id**, not its `variableName` → read the
    template's `userDefinedFields`, map name → id, then post.
21. **Browser pane cannot fetch files from a local server** → stage the file on the frontend
    container's nginx root (same origin) and remove it afterwards, or run the API scripts with the
    session token file.
22. **A table typed into a RICH_TEXT value looked wrong (doubled headers, a column one character
    wide)** → the template had collapsed the original's real cells into one merged cell, so the
    only way to get columns back was a table inside a table cell → give each column its own cell
    and its own tag (`${in_scope_ips_1..4}`). Real iSec reports never nest; copy the original's
    cells so widths, borders and shading come with them, and top-align them so a short column
    starts level with a long one.
23. **Columns stopped lining up under a merged header in the generated file** → the table sized
    its rows in percentages and mixed 4-column header rows with 1- and 2-column data rows;
    LibreOffice recomputes those percentages when it refreshes the TOC and lands on an extra
    1-twip grid column → pin the table and every cell to fixed twips (`type="dxa"`) matching
    `tblGrid`, so there is nothing left to round.
24. **A test fixture DOCX would not open** → docx4j's `WordprocessingMLPackage.load` walks every
    package relationship eagerly, and `_rels/.rels` still pointed at `docProps/app.xml` and
    `docProps/core.xml`, which the fixture never carried. It fails with
    `Docx4JException: Failed to getPart` caused by
    `MalformedURLException: Cannot invoke "String.length()" because "spec" is null`, which reads
    like bad XML but means a part is missing → drop the dangling relationships, or copy the parts in.
25. **A column in a typed table collapsed to one character wide** → the stylesheet's
    `div { word-break: break-all }` lets an IP address break between any two digits, and with no
    explicit column width the importer auto-fits on content → stop typing tables into cells at all
    (lesson 22); where a table is genuinely needed, set explicit column widths and reset
    `word-break` for that field.

---

## 18. Validation process

A template is complete only when a report generated by Faction from representative data has
been checked in both outputs. Minimum data: a client with contacts and a logo, an assessment with
all UDFs filled (long and short values), at least three findings across different severities with
rich-text descriptions, tables and screenshots, and one finding with several assets.

Checklist, in this order:

1. **Unresolved tags**: unzip the generated DOCX and grep `document.xml`, headers and footers for
   `${` (expect none; extension placeholders are the only allowed exception).
2. **Cover**: title, date wording, client logo position and size (measure with PyMuPDF; compare
   with the placeholder's position from the original render).
3. **Headers and footers**: background band on every body page, page number in its tab, client
   logo next to the wordmark on every page from page 2, first-page variants correct.
4. **Front matter**: document control values, distribution list rows, document history, dates.
5. **Summary table**: one row per finding, colours from the map, no config row, no leftover
   placeholder rows, "no issues" row when zero findings.
6. **Findings**: numbering 5.1.1…, severity fill, CVSS link, description/impact/recommendation in
   their cells, proof of concept with figures, one finding per page, no finding swallowed by the
   previous one (long content test).
7. **Fonts**: PDF font list shows the template family (Calibri), not Carlito/DejaVu.
8. **Layout**: page count plausible, no blank pages, tables not broken oddly, TOC correct in the
   PDF.
9. **Logo shapes**: repeat with a square and a very wide logo; both must stay inside their boxes.
10. **Both outputs**: the downloaded DOCX opened in Word (borders, boxes, numbering) and Faction's
    PDF (Preview / download). Compare side by side with the original template's render.

**Never conclude something is fixed because the DOCX XML looks right; measure the rendered PDF.**
Proven repeatedly on the Network template:

- Hyperlink targets: read the PDF's link annotations (PyMuPDF `page.get_links()`), not the DOCX
  relationships.
- Column alignment: take the vertical rules out of `page.get_drawings()` and compare the widths.
  That turned a table that "looked fine" into a measured 130.6 / 130.6 / 130.6 / 130.7 pt.
- Border colour: the same drawings carry the stroke colour, so `(0.75, 0.75, 0.75)` confirms
  `BFBFBF` rather than black.
- Chart numbers: read the chart part's cached values **and** the cells of its embedded workbook out
  of the generated file, and check they agree with each other and with the table beside them.

Tools that do this mechanically: `tools/faction/e2e_client.py generate` (generate, download,
grep tags, count occurrences), `tools/verify/inspect_placement.py` (where images landed),
`tools/verify/verify_v9.py` (Word + LibreOffice render, measurements, comparison sheet),
`tools/verify/verify_network_v3.py` and `verify_network_v5.py` (charts, CVSS links, scope tables
read back out of a generated report),
`tools/faction/minio_pull.sh` (copy the generated files straight out of the MinIO volume when the
API session is not available).

---

## 19. Repeatable workflow for a new template

1. Inspect the original DOCX: sections, placeholders `{…}`, repeated structures, images, text
   boxes, headers/footers, tables and their styles, numbering definitions. Render it (Word → PDF)
   as the reference picture.
2. List every field: static / built-in / UDF / gap, in a mapping document with the same section
   numbers as the template.
3. Create the assessment type, the UDF JSON (reusing the shared names), the CSS, in Faction.
4. Script the transformation of the DOCX (copy `tools/template-edits/` and adapt paths and
   geometry): placeholders → tags, single loop row and single block, config rows merged, notes page
   removed, highlights stripped, `TableGridLight` restyled, cover boxes fixed.
5. Apply the client-logo recipe: measure the placeholder(s), put `${clientImage logo width=W height=H}`
   in a text box sized to the placeholder (cover) and in each footer band box.
6. Apply the page-number recipe if the original uses a frame.
7. Build the summary table and the findings block exactly as in sections 9 and 10, then adapt the
   columns and rows to the assessment type.
8. Produce the two files (annotated master with "Faction Mapping" comments at every gap, comment-free
   upload copy). Open both in Word once.
9. Run the engine offline if a fork checkout is available (a throwaway JUnit test that loads the
   DOCX, as done in this project) or upload to a throwaway template in the local Faction
   ("<Name> (test)") and generate on a throwaway assessment.
10. Validate per section 18; fix; repeat. Keep each version's upload file in `samples/`.
11. Upload to the real template, generate on a real assessment, validate again, publish the docs
    (mapping document, backend-changes document, README rows).
12. Record in the mapping document's history what changed and how it was verified.

**Feeding the checklist chart.** `${chartData checklist}` counts the checklists attached to the
assessment, so a test assessment needs one before the chart shows anything. Three API calls, all
proven on the Network template:

1. `POST /api/v1/checklist-templates` with
   `{name, assessmentTypeId, questions: [{text, order}], preventClosure}`.
2. `POST /api/v1/assessments/{id}/checklists` with `{templateId}`.
3. `PUT /api/v1/assessments/{id}/checklists/{checklistId}` with
   `{responses: [{questionId, questionText, result, comment, order}]}`, `result` being `PASS`,
   `FAIL` or `NA`.

The 13 iSec Network checklist rows were extracted from the template's own 4.1 table and are kept as
`tools/faction/network_checklist_questions.json`.

---

## 20. Tools index (`tools/`)

All scripts have hard-coded paths from this project (session scratchpads, the Web template,
OneBank ids). Copy, then edit the constants at the top before use.

| Script | Does |
|---|---|
| `template-edits/cover_fix.py` | v3→v4: strips every `w:highlight`, widens and left-aligns the cover date box (DrawingML + VML), removes empty runs. |
| `template-edits/table_fix.py` | v4→v5: restyles `TableGridLight` → `TableGrid`, writes explicit 0.5 pt `BFBFBF` borders, completes partial merged cells, keeps nil edges; also strips comments for the upload copy. |
| `template-edits/build_v6.py` | Cover placeholder picture → `${clientImage}` tag in a box narrowed to the placeholder; removes footer placeholders and a stray body tag. Geometry computed from the placeholder's XML. |
| `template-edits/build_v7.py` | Footer page number: frame → page-anchored text box at the tab position measured from a Word render. |
| `template-edits/build_v8.py` | Restores the footer placeholder box (from the original template) holding a `${clientImage}` tag; cover tag gets `width= height=`. |
| `template-edits/build_v9.py` | Resizes and repositions the footer logo box from band measurements (bar position, wordmark centre). |
| `template-edits/build_network_v1.py` | The whole Network conversion in one pass: highlights, notes page, cover and footer boxes, every `{placeholder}` → tag, tables rebuilt, borders, the `5.1.%1` numbering, master + upload copy. |
| `template-edits/build_network_v2.py` | v1→v2: blank page after the TOC removed, 1.3 Author inline, 2.3 SmartArt → `methodology.png` rendered from Word, 5.x affected assets centred. |
| `template-edits/build_network_v3.py` | v2→v3: `${chartData checklist}` and `${chartData severity}` marker paragraphs before the two native charts; `${cvssString link}` → `${cvssLink View CVSS Metrics}`. |
| `template-edits/build_network_v4.py` | v3→v4: the merged scope and credentials cells split back into the original's own cells, one RICH_TEXT tag each, so nothing is nested. |
| `template-edits/build_network_v5.py` | v4→v5: the light `BFBFBF` line restored on the 12 tables the original draws that way (style plus direct `tblPr` borders); the 3.1.1 table pinned to fixed twips. |
| `verify/build_placement_test.py` | Builds a probe template with the tag in every kind of place (cover box, cell, right-aligned, mixed, unknown slot, header) to learn what the engine honours. |
| `verify/inspect_placement.py` | Reports where images landed in a generated DOCX (boxes, cells, body, header) with sizes. |
| `verify/render_where.py` | Renders template and generated pages (Word COM + PyMuPDF), boxes the tag/image, side-by-side sheet. |
| `verify/verify_v9.py` | Word and container-LibreOffice renders of an engine output, logo and page-number measurements, band crop, comparison sheet. |
| `verify/verify_network_v3.py`, `verify/verify_network_v5.py` | Read a generated Network report back and assert it: both charts against the engagement's real data (cached values and embedded workbook), one NVD link per finding carrying that finding's vector, the scope and credentials cells, no surviving tag or marker. |
| `faction/e2e_client.py` | `setup`: upload template file, import UDFs by variable name, create client + contacts + logo, attach the application. `generate`: generate, poll, download DOCX/PDF, grep unresolved tags. Needs a session token file. |
| `faction/e2e_clone.py` | Clones an assessment (field values + findings) onto a client-owned application and generates. |
| `faction/minio_pull.sh` | Copies the latest generated DOCX/PDF of an assessment out of the MinIO volume with a helper container. |
| `faction/wsl_mvn_mergecheck.sh` | Runs Maven inside Docker (WSL) against a checkout, with the cached `.m2` volume; used for tests and offline compiles. |
| `faction/rebuild_backend_clean.sh` | `docker compose build --no-cache backend` + restart + health wait + provenance check. |
| `faction/kali_restore_stack.sh` | One-shot restore of the stack into the VM's Docker engine from `docker-migration/`: checksums, images, both data volumes, `compose up`, health wait. `--force` overwrites non-empty volumes. |
| `faction/kali_stack.sh` | Day-to-day `start` / `stop` / `restart` / `status` / `logs` for the stack on the VM. |
| `faction/kali_mvn_clientimage.sh` | Maven for the fork backend inside Docker on the VM, with the persistent `faction-m2` volume. |
| `faction/kali_rebuild_backend.sh` | Rebuilds the backend image from the `isec-faction-clientimage` worktree and restarts the container (the Dockerfile packages with `-DskipTests`). |
| `faction/kali_stage_files_network.sh` | Puts the template, UDF JSON, CSS and checklist JSON on the frontend nginx root so the signed-in page can upload them same-origin. |
| `faction/kali_minio_pull_network.sh` | Copies an assessment's newest generated DOCX and PDF out of the MinIO volume on the VM. |
| `faction/sync_to_kali.sh` | Copies the given project-relative paths from the Windows backup copy to the live Kali working directory and prints their checksums. |
| `faction/Set-FactionKaliPortProxy.ps1` | Windows, elevated: repoints the `127.0.0.1:8080` portproxy rule at the VM's current IP (read from `vmrun`) and verifies `http://localhost:8080`. |
| `faction/network_checklist_questions.json` | The 13 iSec Network checklist rows taken from the template's own 4.1 table, in the order the checklist template wants them. |

Requirements on the workstation: Python 3 with `pywin32` (Word COM export), `PyMuPDF`, `Pillow`,
`numpy`, `lxml`; Microsoft Word; the Kali VM with Docker for the local Faction (section 21); `curl`.
The `kali_*` scripts run on the VM over `ssh kali`, with the project at `/home/kali/Reporting System/`.

---

## 21. Environment notes for the local Faction

- **Where it runs**: inside a Kali VM on VMware Workstation (`D:\exported vm\Kareem's vm.vmx`, NAT
  on VMnet8, guest `192.168.159.128`, 8 GB RAM, 4 vCPU). The live working directory is
  `/home/kali/Reporting System/`; the Windows folder `C:\Users\ISEC\Desktop\Reporting System` is now
  the backup copy, and edits made there are pushed with
  `tools/faction/sync_to_kali.sh <project-relative-path>...`. Shell access is `ssh kali` (key auth
  already configured, passwordless sudo, the `kali` user in the `docker` group).
- **Stack**: compose project `owasp-faction-2`, four containers (db, minio, backend, frontend on
  port 8080), images `isec-faction-backend:local` / `isec-faction-frontend:local` built from the
  fork checkout with `docker-compose.yml` + `docker-compose.local.yml`; `.env` holds the secrets
  (never print them). All four carry `restart=unless-stopped` and `docker.service` is enabled, so
  Faction returns by itself after a VM reboot; `tools/faction/kali_stack.sh` is the manual control.
- **Reaching it from Windows**: `http://localhost:8080` goes through a netsh portproxy rule
  `127.0.0.1:8080 -> 192.168.159.128:8080`. The rule needs an elevated shell;
  `tools/faction/Set-FactionKaliPortProxy.ps1` reads the VM's current IP from `vmrun` and rewrites
  it. Re-run it if the VM's DHCP lease ever changes.
- **WSL is the backup, not the environment**: the `kali-linux` distro is kept untouched and is no
  longer active. WSL2 itself currently cannot start on this machine
  (`HCS_E_HYPERV_NOT_INSTALLED`, the Windows hypervisor is off), which is why VMware works: with
  `hypervisorlaunchtype` off, VMware runs natively.
- **Building the fork**: Maven for the backend runs in Docker on the VM,
  `tools/faction/kali_mvn_clientimage.sh` (persistent `faction-m2` volume); the backend image is
  rebuilt and restarted by `tools/faction/kali_rebuild_backend.sh`. That Dockerfile packages with
  `-DskipTests`, so the test suite must be run separately.
- **API paths that matter**: templates `/api/v1/report-templates/{id}` (`PUT` for UDFs, `POST …/file`
  multipart for the DOCX, `GET …/file` to download it), organizations `/api/v1/organizations/{id}`
  and `…/images`, assessments `/api/v1/assessments`, reports `/api/v1/reports/{id}/generate`,
  `…/documents`, `…/documents/DOCX/content`, terminology `/api/v1/config/terminology`.
- **Session token**: the person signs in; scripts read the JWT from a local file that is never
  printed. From the signed-in browser page, API calls can be made with `fetch` and the token from
  `localStorage`; file transfer into that page needs the file served from the same origin.
- **Throwaway objects for tests**: a template named "<Name> (test)" and an assessment named the same,
  on an application attached to the test client. Delete them when done.
- **Known local gap**: the ENCRYPTED_PDF document always fails with "SSO_ENCRYPTION_KEY is not
  configured", because that value is not set in `.env`. DOCX and PDF are unaffected.

---

## 22. Quick reference

```
Tags:            ${asmtName} ${asmtType} ${asmtAppid} ${asmtClient} ${asmtClient_<field>}
                 ${asmtAssessor} ${asmtAssessors_Comma|_Lines|_Bullets}
                 ${today MMMM d, yyyy} ${asmtStart dd/MM/yyyy} ${asmtEnd dd/MM/yyyy}
                 ${riskCount9..5} ${riskTotal}
                 ${clientContactTable} ${loop}${contactName} ${contactTitle} ${contactEmail}
                 ${clientContacts_Lines|_Bullets|_Comma}
                 ${clientImage <slot> width=<px> height=<px>}
Table:           ${vulnTable [Section]} ${cells k=v,…} ${noIssuesText …} ${loop} ${count}
                 ${vulnName} ${severity}(FAC701) ${cvssScore} ${assetLocation} ${category} ${<udf>}
Block:           ${fiBegin [Section]} ${fill k=v,…} ${vulnName} ${desc} ${rec} ${details}
                 ${cvssString link} | ${cvssLink <label>}  ${pageBreak} ${fiEnd}
                 ${if-section S} … ${end-section S}
Charts:          ${chartData severity} ${chartData checklist}  (alone in the body paragraph before the chart)
Sizes:           1 cm = 37.8 px = 360000 EMU;  1 px = 9525 EMU;  page width cap 15.9 cm
Web v9 geometry: cover logo 139x54 px (3.67x1.44 cm), box 4.29 cm wide at 15.05 cm from column
                 footer logo 64x25 px (1.69x0.66 cm), box 2.26 cm at 2.90 cm from column, 0.12 cm below the footer paragraph
                 page number box 1.10x0.80 cm at page 0.54/28.71 cm
Colours:         Critical C00000, High FFC000, Medium F8F200 (table) / FFFF00 (block), Low 00B050, Informational 00B0F0, Status grey 808080
```
