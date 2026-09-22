# Faction 2 variables mapped to the iSec Internal/External Network Penetration Testing template

Template: `Documents\iSec\Reports\Templates\Old Templates\1. iSec_INT_EXTNWPT_Template (1).docx`.
Tagged result: `iSec_INT_EXTNWPT_Template_Faction.docx` (annotated master, v5) and
`iSec_INT_EXTNWPT_Template_Faction_UPLOAD.docx` (the file uploaded to Faction).
Built by `tools/template-edits/build_network_v1.py` from the original in one pass, then `build_network_v2.py` … `build_network_v5.py`.
Faction: the `isec-faction` fork at commit `a811886` or later (client images in headers and footers need `b77a81b`).

The engine rules, the complete variable reference and the client package are documented once, in
`Faction2_WAPT_Template_Mapping.md` sections 1 and 2, and the conversion recipes in `AGENT.md`. This
document only records what is specific to the Network template. Status words are the same as in the Web
mapping: DIRECT, UDF, PARTIAL, GAP, STATIC.

## 1. How the Network template differs from the Web template

The two originals share the cover, the headers and footers, the media, the styles and most sections. The
package parts `header1–3.xml`, `footer1–4.xml`, all pictures and the cover geometry are byte-identical to the
Web source, which is why the Web recipes (cover boxes, footer page number, footer logo) apply unchanged.
What differs:

| Area | Network template | Web template |
|---|---|---|
| Cover / 1.1 title | `{ Client Name + Project Name } {Internal/External} Penetration Testing Report` | no network type |
| 1.1 | Extra row "Document ID: TO BE WRITTEN BY REVIEWERS" | no such row |
| 2.1 | `{a/an} {Internal/External} network penetration testing`; tester note "Must mention limitations if exists." | "web application penetration testing" |
| 2.3 / 2.4 | Methodology diagram as SmartArt; 2.4 checklist summary as a native bar chart (`chart1.xml`, embedded workbook) | same SmartArt; Web checklist chart + OWASP chart (2.5) |
| 2.5–2.8 | Findings Distribution, Chart, Risk Criteria, Summary of Findings (numbered 2.5–2.8) | numbered 2.6–2.9 |
| 3.1 | Second sentence "This Penetration Testing on { Project Name } was done on {a Testing/Production} Environment." | environment in the 3.1.1 table |
| 3.1.1 | "In Scope IPs" (4-column list) and "Out Of Scope IPs and Ports" (Functions Names / Reason) | Application Name / URL / Version / Environment table + 3.1.2 Scope Functions |
| 3.1.2 | Provided Credentials | 3.1.3 |
| 3.2 | Sub-heading "3.2.1 Initial Test" above the limitations | no sub-heading |
| 4.1 | iSec Network Penetration Testing Checklist (13 rows); no OWASP checklist | 4.1 Web checklist (52 rows) + 4.2 OWASP |
| 5.x table | Severity / CVSS / Status, Affected Assets, iSec Check List, Description, Impact, Recommendation | plus an "OWASP Top Ten" row |
| 6.0 | Standard bullet + 4 example bullets (highlighted) | same idea |

Everything else (Disclaimer, Methodology, Risk Criteria tables, Report Organization, 4.2 iSec Role, Appendices)
is the same static text.

## 2. Template sections filled by built-in variables

| Section | Item | Tag / mapping | Status |
|---|---|---|---|
| Cover | `{ Client Name + Project Name } {Internal/External} Penetration Testing Report` (text box) | `${asmtClient} ${project_name} ${network_type} Penetration Testing Report` | DIRECT + UDF |
| Cover | `{Month DayNN, Year}` (text box) | `${today MMMM d, yyyy}`; box widened to 2.5 in, left-aligned (Web v4 fix) | PARTIAL (no ordinal suffix) |
| Cover | "Network Penetration Testing Report" | Static. | STATIC |
| Cover | Client logo (top right, where "[Insert Image Here]" was) | `${clientImage logo width=139 height=54}` alone in the text box narrowed to the placeholder (4.29 cm wide at 15.05 cm from the column edge): the client record's `logo` fitted into a 3.67 × 1.44 cm box. Same geometry as Web v9. | DIRECT |
| Notes For Penetration Testers page | Whole page | Deleted from the template (no conditional page removal). | GAP (shared 5.16) |
| Table of Contents | Native Word TOC | Kept; Faction's LibreOffice pass refreshes it. The stale entries for "5.1 { Project Name 1} - {Target URL}" and "5.1.2 … 5.1.5" disappear at refresh. | DIRECT |
| Footer band (pages 2 onward, footers 2 and 4) | Client logo next to the iSec wordmark | `${clientImage logo width=64 height=25}` in the small text box after the separator bar (footers copied from Web v9, which come from the same source XML). | DIRECT |
| Footer band | Page number in the pink tab | Page-anchored text box with the PAGE field (Web v7 fix); the original Word frame is dropped by LibreOffice. | STATIC (fixed) |
| 1.1 | Document Title | `${asmtClient} ${project_name} ${network_type} Penetration Testing Report v${report_version}` | DIRECT + UDF |
| 1.1 | Document ID | `${document_id}` (STRING UDF, empty by default) | UDF |
| 1.1 | Classification, Description | Static. | STATIC |
| 1.1 | Date of Issue | `${today MMMM d, yyyy}` | DIRECT |
| 1.2 | Diffusion List | `${clientContactTable}` + `${noIssuesText No distribution list recorded for this client.}` in one merged row, then the `${loop}` row `${loop}${contactName}` / `${contactTitle}` / `${contactEmail}` | DIRECT |
| 1.3 | Version, Report Type | `${report_version}` and "Testing", each merged vertically over the four rows | UDF / STATIC |
| 1.3 | Start / End Date (row 1) | `${asmtStart dd/MM/yyyy}` / `${asmtEnd dd/MM/yyyy}` (planned end) | DIRECT |
| 1.3 | Author (row 1) | `${asmtAssessor}` inline (first assessor; keeps the cell centred). `${asmtAssessors_Comma}` is rendered as a left-aligned HTML paragraph, see v2. | DIRECT |
| 1.3 | Description (row 1) | `${asmt_phase}` | UDF |
| 1.3 | Reviewer / approver rows | `${first_reviewer}`, `${second_reviewer}`, `${approver}`; their dates stay manual | UDF, GAP 5.3 |
| 2.2 | `{ Client Name With No Abbreviations }` | `${asmtClient}` | DIRECT |
| 2.3 | Methodology diagram (SmartArt) | Replaced in v2 by a picture rendered from Word (`methodology.png`, 18 x 10.96 cm, centred): LibreOffice lays SmartArt out differently. | STATIC (fixed in v2) |
| 2.4 | Summary of iSec Network checklist (native bar chart fed by the embedded workbook) | `${chartData checklist}` alone in the paragraph before the chart (v3): the engine writes the assessment's PASS count into the "Secure" series and FAIL into "Vulnerable", in the chart's cached values and in its embedded workbook, then deletes the marker. | DIRECT |
| 2.5 | Critical … Informational counts | `${riskCount9}` … `${riskCount5}` | DIRECT |
| 2.6 | Findings Distribution Chart | `${chartData severity}` alone in the paragraph before the chart (v3): per-severity finding counts in the series and the workbook, so this chart and the 2.5 counts / 2.8 table cannot disagree. | DIRECT |
| 2.7 | Risk Criteria tables | Static. | STATIC |
| 2.8 | Summary of Findings | `${vulnTable}` layout, section 5.1 below. Column header "Affected URL" kept as in the original. | DIRECT |
| 3.3 | Report Organization | Static. | STATIC |
| 4.1 | iSec Network Penetration Testing Checklist | Manual (Done / Status fills). | GAP 5.6 |
| 4.2 | iSec Role in Remediation Phase | Static. | STATIC |
| 5.1 | `5.1 { Project Name 1} - {Target URL}` | `5.1 ${project_name} - ${target_scope}` | UDF |
| 5.1.N | Finding heading | Heading 3 `${vulnName}` numbered by a new Word numbering definition `5.1.%1` (numbering `abstractNum 20` / `num 21`) | DIRECT |
| 5.x | Severity / CVSS / Status | `${severity}` (fill `FAC701`), `${cvssScore} (${cvssLink View CVSS Metrics})` since v3 — the label is the link and its target is the NVD calculator for this finding's own vector — and `${asmt_phase}` (static grey) | DIRECT |
| 5.x | Affected Assets | `${assetLocation}` (one string; several hosts separated by commas), centred since v2 | PARTIAL 5.11 |
| 5.x | iSec Check List | `${isec_checklist_ref}` | UDF |
| 5.x | Description / Impact / Recommendation | `${desc}` / `${impact_narrative}` / `${rec}`, each the only text of its cell | DIRECT / UDF |
| 5.x | Proof Of Concept | `${details}` under the Heading 4; the page break after it closes the finding | DIRECT |
| 6.0 | Recommendation | `${recommendations}` | UDF |
| 7.1 / 7.2 | Appendices | Static. | STATIC |

## 3. User-defined fields (`Faction_INT_EXTNWPT_UDF_Definitions.json`, 22 fields)

Shared names (same meaning as the Web and MAPT templates): `summary1`, `summary2`, `report_version`,
`asmt_phase`, `test_type`, `testing_hours`, `project_name`, `environment`, `first_reviewer`,
`second_reviewer`, `approver`, `provided_credentials`, `limitations`, `recommendations`;
vulnerability scope `affected_user`, `isec_checklist_ref`, `impact_narrative`.

Network-specific:

| Variable | Type | Where | Note |
|---|---|---|---|
| `network_type` | DROPDOWN Internal / External / Internal and External | cover, 1.1, 2.1 | 2.1 reads "an ${network_type} network penetration testing"; both values take "an", so the article is static. |
| `target_scope` | STRING | 5.1 heading | Short label of the tested target ("Internal Network", "10.10.0.0/16"). Replaces the Web template's `app_url` there. |
| `in_scope_ips_1` … `_4` | RICH_TEXT | 3.1.1 "In Scope IPs", one per column | Type a bulleted host list. No table: the row is four real cells, as in the iSec reports. Leave a column empty when fewer are needed. |
| `out_of_scope_ips` | RICH_TEXT, default `N/A` | 3.1.1 merged cell under "Functions Names / Reason" | Insert a 2-column table in the editor. |
| `document_id` | STRING | 1.1 Document ID | Written by the reviewers; Faction has no document-ID variable. |

Not needed here (Web only): `app_url`, `app_version`, `in_scope_functions`, `out_of_scope_functions`,
`owasp_top_ten`. `environment` is kept because 3.1 prints "a ${environment} Environment".

## 4. Gaps

The shared gaps of `Faction2_WAPT_Template_Mapping.md` section 5 apply (checklists, reviewers, conditional
sentences, one-string assets, ordinal dates, the notes page). Two of them are closed for this template from
v3: the two native charts are fed from report data by `${chartData severity}` / `${chartData checklist}`, and
the CVSS cell links a label to the finding's own NVD URL through `${cvssLink ...}`. Network-specific:

| Row | Need | Status |
|---|---|---|
| N.1 | 2.4 checklist summary chart from the assessment's checklist | Closed in v3. A `${chartData checklist}` paragraph before the chart makes the engine write the real PASS / FAIL counts into the chart's cached values and its embedded workbook. Needs an iSec checklist attached to the assessment and answered; with none attached the counts are zero and the chart draws empty on purpose, because leaving the template's placeholder numbers would read as real results. |
| N.2 | Scope hosts / ranges / ports as a structured table | Closed in v4. The original's own cells are back, one RICH_TEXT UDF per column (`in_scope_ips_1`…`_4`, `out_of_scope_ips` / `out_of_scope_reason`, `credential_users` / `credential_descriptions`), so a tester types a bulleted list per column and nothing is nested. |
| N.3 | Findings grouped per target (several 5.x groups) | One flat `${fiBegin}` block. Per-target groups need Faction report sections (`${if-section S}` / `${fiBegin S}` per target) and findings filed under sections. |
| N.4 | Column header "Affected URL" in 2.8 and "Functions Names" in 3.1.1 | Kept verbatim from the original; rename in Word if wanted (static text). |
| N.5 | A table typed into a RICH_TEXT value that sits in a table cell (3.1.1 in/out-of-scope, 3.1.2 credentials, as they were built in v1-v3) | Works when the value ends with an empty paragraph after the table (the editor adds one; an API-written value must include `<p></p>`). Without it the cell's last element is a table, which is invalid OOXML, and LibreOffice unwraps the outer table (headers become paragraphs). Closed in v3: the engine appends the empty paragraph itself when replaced rich text in a cell ends with a table (`keepCellClosed`), so an API-written value no longer has to. |

## 5. Layout of the dynamic blocks

5.1 Section 2.8 Summary of Findings (`${vulnTable}`), rows in order:

| # | Vulnerability | Risk | CVSS 3.1 | Affected URL | Affected User | Status |
|---|---|---|---|---|---|---|
| One merged cell (7 columns) with three paragraphs: `${vulnTable}` · `${cells Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0,Initial Test=808080,Retest=808080,Fixed=92D050,Not Fixed=C00000,Partially Fixed=FFC000}` · `${noIssuesText No vulnerabilities were identified during this assessment.}` | | | | | | |
| `${loop}` (column auto-number kept) | `${vulnName}` | `${severity}` (fill `FAC701`) | `${cvssScore}` | `${assetLocation}` | `${affected_user}` | `${asmt_phase}` (fill `808080`) |

5.2 Section 5.x, one finding, top-level paragraphs in order:

```
5.1 ${project_name} - ${target_scope}          (Heading 2, outside the block)
${fiBegin}
${fill Critical=C00000,High=FFC000,Medium=FFFF00,Low=00B050,Informational=00B0F0,Initial Test=808080,Retest=808080,Fixed=92D050,Not Fixed=C00000,Partially Fixed=FFC000}
Heading 3:  ${vulnName}                        ← numbering "5.1.%1"
[table]  Severity ${severity} | CVSS 3.1 ${cvssScore} (${cvssString link}) | Status ${asmt_phase}
         Affected Assets  | ${assetLocation}
         iSec Check List  | ${isec_checklist_ref}
         Description      | ${desc}
         Impact           | ${impact_narrative}
         Recommendation   | ${rec}
(empty paragraph, page break)
Heading 4:  Proof Of Concept
${details}
(empty paragraph, page break)
${fiEnd}
```

## 6. Changes made to the DOCX (v1) and how they were verified

1. All 143 highlights removed (yellow and red, runs and paragraph marks).
2. Cover: logo placeholder picture → `${clientImage logo width=139 height=54}` in the narrowed box (same
   EMU geometry as Web v9: 1545601 wide at 5416510); date box widened; title and date tagged.
3. "Notes For Penetration Testers" page removed (23 body elements).
4. Every `{placeholder}` replaced as in sections 2 and 3; the "Must mention limitations" note removed, its
   page break kept; 3.2.1 second note paragraph removed.
5. 1.2, 1.3, 2.8, 3.1.1, 3.1.2 tables rebuilt as described; placeholder rows and findings 2–5 deleted
   (40 body elements).
6. 12 `TableGridLight` tables restyled `TableGrid` with explicit 0.5 pt `BFBFBF` borders (187 cells given
   borders, 9 partial merged cells completed).
7. Numbering definition `5.1.%1` added; the `${vulnName}` heading uses it.
8. Footers 2, 3 and 4 replaced by the Web v9 footers (page-number text box, footer logo box).
9. Annotated master: 19 "Faction Mapping" comments; upload copy: none.

Verified on 2026-09-21: both files open in Word (20 pages, 17 tables, 23 shapes, no repair prompt);
Word PDF shows every tag in place (cover logo tag at x = 17.0 cm, date at 2.1 / 28.7 cm, footer logo tag
at 4.5 / 28.3 cm, page numbers in the tab); the backend container's LibreOffice round trip keeps all
borders (no empty `tcBorders`), all 20 text boxes, the page numbers and the tags, and renders in Calibri.
Faction generation test: see the history below.

History:

- v1 (2026-09-21): first conversion, built by `build_network_v1.py`; offline verification as above.
- v1 verified on Faction (2026-09-21): template "iSec Network Penetration Test (Internal/External)" (id `7c032499-3103-44e2-bbd4-3d2347a05391`,
  type "Network Assessment", font Calibri, CVSS 3.1, 22 fields, `iSec_INT_EXTNWPT_Template_Faction_UPLOAD.docx`), generated on the
  throwaway assessment "Network (test)" (id `8cb7c223-a95d-40d7-8344-af43607899aa`, OneBank client, 17 variables, 3 findings with
  tables and code): no unresolved tag in body, headers or footers; cover logo 3.68 x 1.43 cm at 16.62 / 0.75 cm; footer logo
  1.69 x 0.66 cm at 4.45 / 28.22 cm on every page from page 3; page numbers in the tab; Calibri (+ Liberation Mono for code);
  24 pages; 5.1.1-5.1.3 numbered with their Proof Of Concept pages; TOC refreshed; distribution list, document history,
  reviewers, severity fills and CVSS links correct. Output kept as `samples/Sample_Network_Report_v1.docx` / `_faction.pdf`.
  Gap N.5 found on the same run (tables typed into the scope / credentials rich-text cells flatten the outer table).
- v2 (2026-09-21, `tools/template-edits/build_network_v2.py`, review items 1, 2, 3, 9 of the first generated report): (1) the empty
  paragraph after the TOC removed and the TOC field-end and section-break paragraphs set to 1 pt, so LibreOffice's rebuilt TOC no
  longer pushes a blank page before 1.0; (2) 1.3 Author `${asmtAssessor}` inline (centred); (3) the 2.3 SmartArt replaced by
  `methodology.png` (rendered from Word at 300 dpi from the drawing bounds only, 18 x 10.96 cm, centred); (9) 5.x Affected Assets centred. Verified in Word
  (20 pages, no repair) and on Faction (`samples/Sample_Network_Report_v2*`: 23 pages, page 3 = 1.0 Document Control, picture centre
  10.50 cm = page centre, scope and credentials tables with nested tables intact, no unresolved tag). Item 6 (text cut off in 2.8)
  is not in the PDF: the words are complete at 220 dpi and no clip path surrounds those cells (viewer artefact). Items 4, 5, 8
  (charts from real data, NVD link) need the engine: proposal sent, waiting for the go.
- v3 (2026-09-21, `tools/template-edits/build_network_v3.py`, review items 4, 5, 8 and the engine guard for item 7):
  (8) the 5.x CVSS cell's hyperlink text `${cvssString link}` (which printed the raw vector) becomes
  `${cvssLink View CVSS Metrics}`, so the cell reads `9.0 (View CVSS Metrics)` with only the label linked, pointing at the
  NVD calculator built from that finding's own vector and scoring version; (5) a `${chartData severity}` paragraph before the
  2.6 chart; (4) a `${chartData checklist}` paragraph before the 2.4 chart. Both marker paragraphs are deleted during
  generation, so they cost no space. The engine writes each chart's cached values **and** the cells of its embedded workbook,
  so the visible chart and the data behind it stay in step. 53 distinct tags (51 in v2 plus the two markers; the CVSS tag is a
  one-for-one swap), master keeps its 19 mapping comments, UPLOAD none.
  Verified on Faction from the Kali VM (assessment "Network (v3 test)" `d56ced37`, OneBank client, 19 variables,
  3 findings, and a real 13-row iSec Network checklist answered 8 PASS / 5 FAIL): 23 pages, no blank page, no
  unresolved tag in body, headers or footers. 2.4 chart Secure 8 / Vulnerable 5 and 2.6 chart 0-1-1-1-0, each
  matching its embedded workbook and the 2.5 table, read back out of the generated file. Three NVD links, one per
  finding, each carrying that finding's own vector (checked in the rendered PDF's link annotations, not only in
  the DOCX). Scope, out-of-scope and credentials cells keep their nested tables with the outer table intact and a
  closing paragraph, with the typed values deliberately sent without a trailing `<p></p>` so the engine's guard
  was what closed them. Output kept as `samples/Sample_Network_Report_v3.docx` / `_faction.pdf`.
  Known limitation: ENCRYPTED_PDF fails on this install because `SSO_ENCRYPTION_KEY` is not set in `.env`
  (unrelated to the template; DOCX and PDF are unaffected).
- v4 (2026-09-22, `tools/template-edits/build_network_v4.py`): the three merged scope / credentials cells are
  split back into the original's own cells, one tag each, because real iSec reports
  (`iSec_FABMisr_DM_Network_Penetration_Testing_Report_V1.0.docx`) never nest a table inside a table cell:
  3.1.1 In Scope IPs -> `${in_scope_ips_1..4}` in four cells, Out Of Scope -> `${out_of_scope_ips}` /
  `${out_of_scope_reason}`, 3.1.2 -> `${credential_users}` / `${credential_descriptions}`. The cells are copied
  from the original so widths, borders and shading are the originals, top-aligned as FABMisr has them (the
  original centres them, which only looks right while every column holds the same number of lines). The UDF
  set goes from 22 to 27 fields; `in_scope_ips` and `provided_credentials` are gone.
- v5 (2026-09-22, `tools/template-edits/build_network_v5.py`): (a) the v1 conversion had put every table on
  the black-lined `TableGrid` style because LibreOffice dropped `TableGridLight`'s borders. The light grey
  line (single, 0.5 pt, `BFBFBF`) is restored on the 12 tables the original draws that way, written directly
  into `tblPr` as well as through the style so LibreOffice keeps it; the 3 tables the original itself draws
  black (2.5 Findings Distribution, the two risk matrices) are left alone. (b) The 3.1.1 table is pinned to
  fixed twips (10450 = 2612+2612+2613+2613) instead of percentages: mixing 4-column header rows with 1- and
  2-column data rows made LibreOffice round the percentages into a five-column grid with a 1-twip sliver, and
  the columns stopped lining up under the header. Measured in the generated PDF: columns 130.6 / 130.6 /
  130.6 / 130.7 pt, each column's first bullet exactly 130.6 pt from the previous, border colour
  (0.75, 0.75, 0.75) = `BFBFBF`.
- v6 (2026-09-22, `tools/template-edits/build_network_v6.py`): v5's line fix was wrong for the DOCX. LibreOffice writes an
  empty `<w:tcBorders/>` on every cell of any table whose style id is `TableGridLight`, whatever the cells declare, and
  drops table-level `tblBorders`; the DOCX Faction hands out therefore had no table lines at all in Word while the PDF
  (drawn from LibreOffice's own model) looked right. Found on the first real engagement generated from v5 ("External
  Network", Klivvr). The twelve tables go back on `TableGrid` and every cell carries its own `single / 0.5 pt / BFBFBF`
  border; eight cells copied from the original in v4 had none, which is why v4's scope table drew black. Proven before
  generating: a LibreOffice round trip of the template itself (`tools/verify/kali_lo_roundtrip.sh`) returned 17 tables,
  0 empty `tcBorders`, colours intact; the regenerated Klivvr report has 19 tables, 0 empty `tcBorders`, 1334 light edges.
  `verify_network_v6.py` now reads the generated DOCX for empty `tcBorders`. Same run: a client whose record has no
  image in the `logo` slot gets an empty logo box on the cover and in the footers, with no warning; the fix is the
  client's Images page, not the template.
