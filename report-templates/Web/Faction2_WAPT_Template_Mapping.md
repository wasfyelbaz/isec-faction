# Faction 2 variables mapped to the iSec WAPT template

Template: `1._iSec_WAPT_Template.docx` (Desktop copy). Tagged result: `iSec_WAPT_Template_Faction.docx`.
Faction: 2.0.x as in `isec-faction`. Engine `backend/.../util/reporting/DocxUtils.java`, data `ReportData.java`, service `DocxReportGenerationService.java`.
Official docs: https://docs.factionsecurity.com/reporting/docx-templates/

Status words used in sections 3 to 5:

| Status | Meaning |
|---|---|
| DIRECT | A built-in Faction variable covers it as-is. |
| UDF | A User Defined Field created in the Report Designer covers it. This is Faction's intended mechanism for custom content. |
| PARTIAL | Faction covers part of it. The rest needs a new variable. |
| GAP | Nothing in Faction 2 can produce it today. Do not fake it with a look-alike variable. |
| STATIC | Fixed template text. Leave as-is. |

A number after a status, for example `GAP 5.3`, is the row in section 5 that explains the gap.

## 1. Engine rules

1. Tags are `${name}`. A tag must be one uninterrupted run of text in Word.
2. Faction merges split runs (`VariablePrepare`). Spell-check marks, mixed formatting or autocorrect can still break a tag. Type tags with spell-check off and identical formatting.
3. Inline tags are replaced wherever they appear: sentences, table cells, cover-page text boxes.
4. Block tags must be alone in their paragraph. The whole paragraph is replaced by generated content (rich text, TOC, page break, list of assessors).
5. Processing order: section wrappers → `${vulnTable}` tables → `${fiBegin}`…`${fiEnd}` blocks → `${summary1}` / `${summary2}` → assessment variables, dates, UDFs → `${pageBreak}` → extension placeholders → `${TOC}`. Then LibreOffice re-saves the file and refreshes the TOC page numbers.
6. Findings render in the assessment's display order (the order shown on screen). They are not re-sorted by severity.
7. `${severity}` and the keys of `${color}`, `${cells}` and `${fill}` are the severity labels set under Admin → Terminology (default "Critical", "High", "Medium", "Low", "Informational"). `${riskCountN}` and `{[asmtCRITICAL]}` use the internal enum and survive renames.
8. Report sections (`${vulnTable Section}`, `${fiBegin Section}`, `${if-section}`) are gated by `Feature.REPORT_SECTIONS`. Upstream's Community edition returns `false` for it. This fork (commit 2d62270, 2026-09-18) returns `true` for every feature, so sections work here. A finding filed under a section the template has no block for, or an unfiled finding when the template has no bare `${fiBegin}` block, is dropped from the report silently. Section names are matched case-sensitively after spaces become underscores.
9. Headers and footers receive only the simple assessment variables and STRING/DROPDOWN UDFs. Date tags, rich text and lists are not replaced there.
10. Rich text (descriptions, recommendations, details, RICH_TEXT UDFs) is HTML converted to DOCX with the template's CSS from the Report Designer. Images are embedded and capped at 600 px wide.
11. A `${…}` tag still unresolved after all built-ins, and alone in a paragraph, is offered to App Store extensions (ReportManager). If no extension claims it, it stays in the document verbatim.
12. Word charts, SmartArt and embedded Excel data are never touched.

## 2. Variables that work now

2.1 Assessment-level variables (inline unless noted)

| Variable | Value | Rules |
|---|---|---|
| `${asmtName}` | Assessment name | Inline anywhere: cover-page text boxes, headers, footers. |
| `${asmtId}` | Internal assessment ID | Inline. |
| `${asmtAppid}` | Application ID string (the "App ID" configured on the application, not its name) | Inline. The DOCX engine matches `asmtAppid` (lower-case `id`). The docs write `${asmtAppId}`, which only works inside rich-text content. Write `${asmtAppid}` in the DOCX. |
| `${asmtType}` | Assessment type name | Inline. |
| `${asmtAssessor}` | First assigned assessor, full name | Inline. |
| `${asmtAssessor_Email}` | First assessor's email | Inline. `${asmtAssessor_Email link}` inside a Word hyperlink gives a working `mailto:` link. |
| `${asmtAssessors_Lines}` | All assessors, one per line | Block tag in the DOCX (paragraph alone; a table cell is fine). Inline only inside rich text. |
| `${asmtAssessors_Comma}` | All assessors, comma separated | Same rule as `${asmtAssessors_Lines}`. |
| `${asmtAssessors_Bullets}` | All assessors as a bulleted list | Same rule as `${asmtAssessors_Lines}`. |
| `${remediation}` | Remediation manager, full name | Inline. |
| `${asmtTeam}` | Always empty in Faction 2 | Do not use. |
| `${asmtAccessKey}` | Same value as `${asmtId}` (legacy) | Do not use. |
| `${riskCount9}` `${riskCount8}` `${riskCount7}` `${riskCount6}` `${riskCount5}` | Count of Critical / High / Medium / Low / Informational findings | Inline. Works in headers/footers. |
| `${riskTotal}` | Total findings | Inline. |
| `${totalOpenVulns}` / `${totalClosedVulns}` | Findings with no close date / with a close date | Inline. Meant for retest reports. |
| `${summary1}` / `${summary2}` | RICH_TEXT assessment UDFs named exactly `summary1` and `summary2` (default names "Executive Summary" and "Scope") | Block tag. |
| `${TOC}` | Generated table of contents, heading levels 1–3, then a page break | Block tag. The template has a native Word TOC (levels 1–4) that LibreOffice refreshes during generation. Keep the native TOC. Do not add `${TOC}`. |
| `${pageBreak}` | Real page break | Block tag in a top-level body paragraph (not inside a table). Inside a findings block it repeats per finding. |
| `${if-section S}` … `${end-section S}` | Removes everything between the markers when section S has no findings | Each marker alone in a top-level paragraph. Bare `${if-section}` is the default section. Available in this fork (rule 8). |

2.2 Date variables

1. `${today}`, `${asmtStart}`, `${asmtEnd}` take an optional Java `SimpleDateFormat` pattern after a space. Default `MM/dd/yyyy`.
2. `${asmtStart}` is the assessment start date.
3. `${asmtEnd}` is the planned end date, not the completion date.
4. Date tags are replaced only in the document body, not in headers or footers.

| Example | Output |
|---|---|
| `${today MMMM d, yyyy}` | September 18, 2026 |
| `${asmtStart dd/MM/yyyy}` | 21/04/2026 |
| `${asmtEnd EEEE, d MMM yyyy}` | Saturday, 2 May 2026 |

2.3 User Defined Fields (UDFs) from the Report Designer

| Scope | Type | In the DOCX |
|---|---|---|
| Assessment | STRING, DROPDOWN | `${variable_name}` inline anywhere, headers/footers included. |
| Assessment | RICH_TEXT | `${variable_name}` block tag (paragraph alone). Rendered with the template CSS. |
| Vulnerability | STRING, DROPDOWN | `${variable_name}` inline inside a `${vulnTable}` row or a `${fiBegin}` block. |
| Vulnerability | RICH_TEXT | `${variable_name}` block tag inside a row cell or block. |
| Any UDF holding a URL or email | any | `${variable_name link}` inside a Word hyperlink sets the link target. |

1. Variable names must be unique per template.
2. Renaming a variable on a live template disconnects existing values.
3. DROPDOWN/STRING values can drive colours through `${custom-fields}` (see 2.4 and 2.6).

2.4 Vulnerability table (`${vulnTable}`). All of these live inside one Word table.

| Tag | Role |
|---|---|
| `${vulnTable}` / `${vulnTable Section}` | Marks the table. Put it at the start of a cell in its own row. That row is deleted. |
| `${loop}` | At the start of a cell paragraph on the row repeated once per finding (for example cell text `${loop}${count}`). |
| `${loop-N}` | Repeat this row plus the N rows below it. |
| `${color k=v,…}` | Text-colour map. Own row, starts the cell. Example: `${color Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0}` |
| `${cells k=v,…}` | Cell-fill map, same syntax. |
| `${custom-fields var=PLACEHOLDER,…}` | Ties a UDF to a placeholder colour used in the row, so `${color}`/`${cells}` keys can be its values. |
| `${noIssuesText Your text}` | Text for an empty table. Default "No issues detected for this section.". |

1. Config rows are removed from the output.
2. Sentinel colours on the repeating row (text colour or cell fill): `FAC701` → the severity's colour, `FAC702` → likelihood, `FAC703` → impact.

2.5 Per-finding variables (inside a `${vulnTable}` row or a `${fiBegin}` block)

| Variable | Value | Rules |
|---|---|---|
| `${vulnName}` | Finding title | Inline. |
| `${severity}` | Severity label | Inline. |
| `${cvssScore}` | Score, for example `9.0` | Inline. Empty if no score. |
| `${cvssString}` | CVSS vector | Inline. |
| `${cvssString link}` | Hyperlink to the FIRST.org calculator (3.1 or 4.0 per template scoring type) | Must be inside a Word hyperlink. The hyperlink's display text becomes the vector string. Custom text like "View CVSS Metrics" is not preserved. |
| `${assetLocation}` | Asset / URL field of the finding | Inline. One string. Newlines are not turned into separate lines. |
| `${category}` | Vulnerability category name | Inline. "UnCategorized" when none. |
| `${tracking}` | Tracking ID (`VID-10000`…) | Inline. |
| `${vid}` | Internal finding ID | Inline. |
| `${count}` | 1-based row/finding counter | Inline. Resolved in `${vulnTable}` rows only, not in `${fiBegin}` blocks. |
| `${sevId}` | Severity-scoped counter, for example `CV1`, `HV2` | Inline. |
| `${likelihood}` / `${impact}` | Likelihood and Impact ratings (severity-style labels), not narrative text | Inline. Do not use `${impact}` for an "Impact" paragraph. |
| `${remediationStatus}` | `Open` or `Closed` | Inline. |
| `${openedAt}` `${closedAt}` `${closedInDevAt}` `${closedInStagingAt}` | Dates, `MM/dd/yyyy`, no custom format | Inline. |
| `${desc}` | Description (rich text) | Block tag (paragraph alone; may be inside a table cell). |
| `${rec}` | Recommendation (rich text) | Block tag. |
| `${details}` | Details / exploit steps / screenshots (rich text) | Block tag. |
| `${your_udf}` / `${your_udf link}` | Vulnerability UDFs | Inline (STRING/DROPDOWN) or block (RICH_TEXT). |
| `${Figure#.N}` | Inside the rich text of desc/rec/details → "Figure <finding number>.N" | Written in the editor content, not in the DOCX. |

2.6 Findings block (`${fiBegin}` … `${fiEnd}`)

1. `${fiBegin}` and `${fiEnd}` each sit alone in a top-level body paragraph (not inside a table or text box).
2. Everything between them, tables and headings included, is copied once per finding.
3. Config paragraphs inside the block: `${color …}`, `${fill …}` (the block uses `fill`, not `cells`), `${custom-fields …}`, `${noIssuesText …}`. Each must be its own top-level paragraph inside the block.
4. A config tag placed inside a table drops that entire table.
5. Sentinels `FAC701/2/3` work on text colour and on cell fill inside the block.
6. `${pageBreak}` inside the block gives one page per finding.
7. Word heading numbering (multilevel list on Heading 3) is preserved. A `${vulnName}` heading auto-numbers 5.1.1, 5.1.2, …

2.7 Legacy rich-text loops

1. `{[asmtCRITICAL]}`, `{[asmtHIGH]}`, `{[asmtMEDIUM]}`, `{[asmtLOW]}`, `{[asmtINFORMATIONAL]}`: a numbered list of finding names at that severity.
2. Resolved only inside rich-text field content (for example an executive summary UDF), not in the DOCX body.

2.8 Extension placeholders (App Store)

1. `${faction-bar-chart}`: severity chart image.
2. `${checklist-<checklist-name> columns=[Question,Status,Comment]}`: assessment checklist table.
3. Both resolve only when the matching ReportManager extension is installed under Admin → System → App Store.
4. The placeholder must be alone in a paragraph.
5. Community edition allows two extensions.

2.9 Client package (the assessment's organization, fork commits 40047cc, 488ca0f, b77a81b)

| Tag | Result |
|---|---|
| `${asmtClient}` | The client's name, inline. |
| `${asmtClient_<variableName>}` | A client custom field, inline (STRING, DROPDOWN) or as a block when alone in its paragraph (RICH_TEXT). |
| `${clientContactTable}` + `${loop}` row with `${contactName}`, `${contactTitle}`, `${contactEmail}` | The distribution list as one table row per contact; the `${noIssuesText …}` row prints when the list is empty. |
| `${clientContacts_Lines}`, `${clientContacts_Bullets}`, `${clientContacts_Comma}` | The distribution list as a block. |
| `${clientImage <name>}` | The client image stored under `<name>` (Clients > edit > Client images), alone in its paragraph: body, table cell, text box, header or footer. Natural size at 96 dpi, capped at the page width. |
| `${clientImage <name> width=<px>}` or `height=<px>` | Scaled proportionally to that width or height. |
| `${clientImage <name> width=<px> height=<px>}` | Fitted into that box, padded and centred, the file's empty margins trimmed first, so every client's logo occupies exactly the same space whatever its shape. 96 px = 2.54 cm. |

The paragraph keeps its own alignment and spacing. A slot the client has not filled leaves nothing behind (the paragraph is removed, or emptied when it is the only one in a cell or text box). The same slot may appear any number of times.

## 3. Template sections filled by built-in variables

| Section | Item | Tag / mapping | Status |
|---|---|---|---|
| Cover | `{ Client Name + Project Name } Penetration Testing Report` (text box) | `${asmtClient} ${project_name} Penetration Testing Report`. `${asmtClient}` is the client (organization) name from the client record (fork `main`, commit 40047cc); `${project_name}` is an assessment UDF. | DIRECT + UDF |
| Cover | `{Month DayNN, Year}` (text box) | `${today MMMM d, yyyy}` → "May 11, 2026". No ordinal suffix ("11th"). | PARTIAL 5.14 |
| Cover | "Web Application Penetration Testing Report" | Static, or `${asmtType}`. | STATIC |
| Cover | iSec logo, shapes | Untouched. | STATIC |
| Cover | Client logo (top right, where "[Insert Image Here]" was) | `${clientImage logo width=139 height=54}` alone in the text box that held the placeholder picture (box 4.29 cm wide at 15.05 cm from the column edge). Prints the client record's `logo` image fitted into a 3.67 × 1.44 cm box, padded and centred, so every client's logo takes the same space; the engine trims the file's empty margins first. Nothing is printed when the client has no `logo` image. | DIRECT |
| Notes For Penetration Testers page | Whole page | No conditional removal exists. Delete the page from the copy you upload to Faction. | GAP 5.16 |
| Table of Contents | Native Word TOC | Keep it. Faction's LibreOffice pass refreshes it and keeps level 4 ("Proof Of Concept"). `${TOC}` would only give levels 1–3. | DIRECT |
| Footer band (pages 2 onward, both footers) | Client logo next to the iSec wordmark (where "[Insert Image Here]" was) | `${clientImage logo width=64 height=25}` alone in the small text box right after the separator bar (box at 2.90 cm from the column edge, centred on the wordmark). Prints the same `logo` image fitted into a 1.69 × 0.66 cm box, the iSec wordmark's own width. Footers are parts of their own; the engine registers the picture on each footer (fork commit b77a81b). | DIRECT |
| Footer band | Page number in the pink tab | A page-anchored text box (0.54 / 28.71 cm, 1.1 × 0.8 cm) holding the PAGE field, white, centred. The original Word frame was dropped by Faction's LibreOffice pass, which left the number invisible. | STATIC (fixed in v7) |
| 1.0 | Client logo | Removed in v6: the logo lives on the cover and in the footer band instead. | — |
| 1.1 | Document Title | `${asmtClient} ${project_name} Penetration Testing Report v${report_version}` (`project_name`, `report_version` are assessment UDFs, see 4). | DIRECT + UDF, PARTIAL 5.3 |
| 1.1 | Classification, Description | Static. | STATIC |
| 1.1 | Date of Issue | `${today MMMM d, yyyy}` | DIRECT |
| 1.3 | Report Type | Static "Testing". | STATIC |
| 1.3 | Start / End Date | `${asmtStart dd/MM/yyyy}` / `${asmtEnd dd/MM/yyyy}` (planned end). | DIRECT |
| 1.3 | Author | `${asmtAssessors_Comma}` as the only text in the cell, or `${asmtAssessor}` inline. | DIRECT |
| 2.3 | Assessment Methodology (+ SmartArt) | Static. | STATIC |
| 2.4 / 2.5 | Summary of iSec / OWASP checklist bar charts | Native Word charts (chart1/chart2) fed by embedded Excel. Faction cannot update chart values. Checklist pass/fail counts are not exposed as variables. | GAP 5.5, 5.7 |
| 2.6 | Critical … Informational counts | `${riskCount9}` `${riskCount8}` `${riskCount7}` `${riskCount6}` `${riskCount5}` | DIRECT |
| 2.6 | Extra retest columns (Fixed / Not Fixed per severity) | Not used in this template (initial test only). Only the totals `${totalOpenVulns}` / `${totalClosedVulns}` exist. | PARTIAL 5.9, retest template only |
| 2.7 | Findings Distribution Chart | Native Word chart (chart3, categories Critical…Informational). Not refreshable. `${faction-bar-chart}` needs the App Store extension and inserts an image, not the styled chart. | GAP 5.5 |
| 2.8, 2.8.1–2.8.3 | Risk Criteria | Static. | STATIC |
| 2.9 | # column | `${loop}${count}` | DIRECT |
| 2.9 | Vulnerability | `${vulnName}` | DIRECT |
| 2.9 | Risk | `${severity}`; cell fill `FAC701`; config row `${cells Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0}` | DIRECT |
| 2.9 | CVSS 3.1 | `${cvssScore}` | DIRECT |
| 2.9 | Affected URL | `${assetLocation}`. `${assetLocation link}` is not supported. For a clickable cell use a vuln UDF `affected_url` with `${affected_url link}`. | DIRECT |
| 3.3 | Report Organization | Static. | STATIC |
| 4.1 / 4.2 | iSec checklist / OWASP Top 10 checklist tables | Extension placeholder `${checklist-isec-web-penetration-testing-checklist columns=[Question,Status,Comment]}`. What it cannot do: see 5.6. Interim: keep the tables static and fill them by hand, or install the extension and accept its layout. | PARTIAL 5.6 |
| 4.3 | iSec Role in Remediation Phase | Static. | STATIC |
| 5.0 | Vulnerability Findings heading | Static. | STATIC |
| 5.1 | `5.1 { Project Name 1} - {Target URL}` heading, one group per target | Static heading `${project_name} - ${app_url}` (UDFs, see 4). Earlier draft: `${asmtName}` plus STRING UDF `target_url`. The tagged DOCX uses the bare `${fiBegin}` block, so all findings render in one group. Per-target groups are now possible with Report Sections (rule 8): one `${if-section S}` / `${fiBegin S}` block per target, as the MAPT template does. | PARTIAL 5.13 |
| 5.1.N | Finding heading `5.1.N Finding N` | Heading 3 paragraph containing `${vulnName}` inside the block. Word numbering supplies 5.1.N. | DIRECT |
| 5.x | Severity cell | `${severity}`; cell fill `FAC701`; block config paragraph `${fill Critical=C00000,High=FFC000,Medium=FFFF00,Low=00B050,Informational=00B0F0}` | DIRECT |
| 5.x | CVSS `9.0 (View CVSS Metrics)` | `${cvssScore}` plus a hyperlink whose text is `${cvssString link}`. The link text becomes the CVSS vector, not "View CVSS Metrics". | PARTIAL 5.10 |
| 5.x | Affected Assets (several URLs, one per line) | `${assetLocation}` gives one string. No line splitting. | PARTIAL 5.11 |
| 5.x | OWASP Top Ten | `${category}` when the vulnerability category list is the OWASP Top 10 entries. Otherwise a UDF, see 4. | DIRECT |
| 5.x | Description | `${desc}` alone in the cell. | DIRECT |
| 5.x | Recommendation | `${rec}` alone in the cell. | DIRECT |
| 5.x | Proof Of Concept steps and screenshots | `${details}` alone in a paragraph under the "Proof Of Concept" heading. Captions via `${Figure#.N}` in the editor. | DIRECT |
| 5.x | Page per finding | `${pageBreak}` as the last paragraph inside the block. | DIRECT |
| 6.0 | Recommendation | Static bullets, or a UDF (see 4). | STATIC |
| 7.1 / 7.2 | Appendix | Static. | STATIC |

2.9 label: the template prints "Info", Faction prints "Informational". Rename the label under Admin → Terminology or accept "Informational". The `${cells}` keys must match whichever label is in force.

## 4. Template sections filled by UDFs

Create these in the Report Designer. Full option lists and defaults: `Faction_Backend_Changes_Needed.md`, section 2.

| Section | Item | UDF (scope) | Type | Note |
|---|---|---|---|---|
| 1.1 / 1.3 | Version | Assessment `report_version` | STRING | Until `${reportVersion}` exists (5.3). |
| 1.2 | Diffusion List (Contact Name / Title / Email) | none: the client record's distribution list | built-in | `${clientContactTable}` in a merged row above the `${loop}` row; the loop row holds `${loop}${contactName}`, `${contactTitle}`, `${contactEmail}`. Empty list prints the `${noIssuesText …}` wording. |
| 1.3 | First / Second Reviewer, Approver | Assessment `first_reviewer`, `second_reviewer`, `approver` | STRING | Interim. GAP 5.3: peer-review data exists in Faction but is not exposed. Reviewer dates: no variable, manual. |
| 2.1 | `{Initial Test/Retest}` | Assessment `asmt_phase` | DROPDOWN | `Initial Test` only. This template is for initial tests; retests use a separate template. |
| 2.1 | `{type} box` | Assessment `test_type` | DROPDOWN | `Black` / `Grey` / `White`. |
| 2.1, 3.1, 3.1.1, 5.1 heading | `{ Project Name }` / Application Name | Assessment `project_name` | STRING | Earlier draft name: `app_name`. Interim: no application-name variable, only `${asmtAppid}` (the ID). GAP 5.1. |
| 2.1 | `{during/after} working hours` | Assessment `testing_hours` | DROPDOWN | Each option holds the full clause. |
| 2.1 | `{./, minimizing the impact … sensitivity}` | Same `testing_hours` option text | DROPDOWN | GAP 5.4: no if/else on values, so the alternative sentence lives in the option text (`after working hours, minimizing …`). |
| 2.2 | `{ Client Name With No Abbreviations }` | none: `${asmtClient}` from the client record | built-in | |
| 2.9 | Affected User | Vulnerability `affected_user` | STRING | |
| 2.9 / 5.x | Status | Assessment `asmt_phase` (same variable as the MAPT template) | DROPDOWN | `Initial Test` only. The cell keeps a static grey `808080` fill; no `${custom-fields}` is needed. The status keys in the `${cells}`/`${fill}` maps (`Initial Test=808080,Retest=808080,Fixed=92D050,Not Fixed=C00000,Partially Fixed=FFC000`) are inert in this template. Built-in alternative: GAP 5.8. |
| 3.1 | Scope of work | Assessment `asmt_phase`, `test_type`, `project_name` | as above | Same UDFs as 2.1. |
| 3.1.1 | Application URL | Assessment `app_url`; `${app_url link}` inside a Word hyperlink | STRING | Interim. GAP 5.1: engagement URLs exist on the assessment but are not exposed. |
| 3.1.1 | Version | Assessment `app_version` | STRING | |
| 3.1.1 | Environment Type | Assessment `environment` | DROPDOWN | |
| 3.1.2 | Scope Functions (in / out of scope) | Assessment `in_scope_functions`, `out_of_scope_functions` (tables allowed in rich text), or `${summary2}` | RICH_TEXT | GAP 5.15: the assessment's own `scope` text field is not exposed. |
| 3.1.3 | Provided Credentials | Assessment `provided_credentials` | RICH_TEXT | |
| 3.2 | Testing Constraints and Limitations | Assessment `limitations` | RICH_TEXT | |
| 5.1 heading | `{Target URL}` | Assessment `app_url` | STRING | Earlier draft name: `target_url`. |
| 5.x | OWASP Top Ten | Vulnerability `owasp_top_ten` | DROPDOWN | Only when `${category}` is not the OWASP Top 10 list. |
| 5.x | iSec Check List | Vulnerability `isec_checklist_ref` | STRING | Interim. GAP 5.12: no link between a finding and checklist items. |
| 5.x | Impact (narrative) | Vulnerability `impact_narrative` | RICH_TEXT | Not `${impact}` (that is a rating label). |
| 6.0 | Recommendation | Assessment `recommendations` | RICH_TEXT | |

## 5. Template sections that need Faction backend work

| Row | Template need | Why Faction cannot do it | Nearest thing, and why it is not a correct mapping |
|---|---|---|---|
| 5.1 | Application name, application URL(s) (client name is solved: `${asmtClient}`) | `ReportData` carries only the application ID string; the application entity is never loaded for the report. | `${asmtAppid}` is an identifier like "APP-0012", not a name. The Web template uses the UDF `project_name` instead. |
| 5.2 | Diffusion list, client logo | Solved on `main` (commits 40047cc, 488ca0f, b77a81b): the client record's distribution list renders through `${clientContactTable}`, its images through `${clientImage <name>}` in the body, headers and footers, with `width=` / `height=` sizing. The client is the assessment's own organization, recorded when the assessment is created from an application that already belongs to a client. | |
| 5.3 | Document history: reviewers, approver, engagement manager, review dates, report version | Peer-review records and `engagementManagerId` exist but are not passed to the template. No report version counter. | `${asmtAssessor}` covers the author only. |
| 5.4 | Conditional sentence depending on a value ("during/after working hours, minimizing…") | No conditional logic on variable values. `${if-section}` only tests whether a section has findings. | None. |
| 5.5 | Native Word bar charts (2.4, 2.5, 2.7) | The engine never touches `word/charts/*.xml` or the embedded workbooks. | `${faction-bar-chart}` (extension) inserts an image with the extension's styling. |
| 5.6 | 4.1 / 4.2 checklist tables with Done + Status columns, "Vulnerable / Not Vulnerable" wording, coloured fills | No native checklist variable. The extension emits one Status column (Pass/Fail/NA), not iSec's "Done" + "Status" pair. It renders an HTML table styled by CSS class (`checklist-…`), not the Word table with the `92D050` / `C00000` / `D9D9D9` fills. It needs the extension installed and counts against the 2-extension limit. | Checklist extension: different columns, labels and styling. |
| 5.7 | Checklist pass/fail counts feeding the 2.4 / 2.5 charts | Not exposed. | None. |
| 5.8 | Finding status "Initial Test / Retest: Fixed / Not Fixed / Partially Fixed". Retest template only; this template prints `Initial Test` in grey. | Only `${remediationStatus}` = Open/Closed. Retest results (`Retest.result` PASS/FAIL) are not exposed. | `${remediationStatus}` loses the retest meaning. A UDF works but must be maintained by hand. |
| 5.9 | Per-severity open/closed counts for the 2.6 retest columns. Retest template only. | Only overall `${totalOpenVulns}` / `${totalClosedVulns}`. | None. |
| 5.10 | CVSS hyperlink displaying "View CVSS Metrics" | `${cvssString link}` overwrites the link text with the vector string. | Accept the vector as link text. |
| 5.11 | Affected Assets as several lines | `${assetLocation}` is one string. No splitting. | One asset per finding, or a RICH_TEXT UDF. |
| 5.12 | "iSec Check List" row linking a finding to checklist items | No relation between vulnerabilities and checklist questions in the model. | Vuln STRING UDF typed by hand. |
| 5.13 | Findings grouped per target under `5.1 {Project} - {URL}` | Needs Report Sections, which this fork now enables (rule 8). Not applied to the tagged DOCX yet: it needs one section per target on the template, every finding filed under a section, and a bare `${fiBegin}` block kept as the Default fallback so unfiled findings are not dropped. | One flat block (current DOCX). |
| 5.14 | Ordinal dates ("May 11th, 2026") | `SimpleDateFormat` has no ordinal token. | `${today MMMM d, yyyy}`. |
| 5.15 | Assessment "Scope" text field | Present on the assessment, not passed to `ReportData`. | `${summary2}` UDF duplicates the content. |
| 5.16 | Auto-removal of the "Notes for Penetration Testers" page | No conditional or instruction blocks. | Delete it in the uploaded copy. |

The variables that close these gaps are listed in `Faction_Backend_Changes_Needed.md`, section 3.

## 6. Layout of the two dynamic blocks

6.1 Section 2.9 Summary of Findings (`${vulnTable}`). Rows of the Word table, in order:

| # | Vulnerability | Risk | CVSS 3.1 | Affected URL | Affected User | Status |
|---|---|---|---|---|---|---|
| One merged cell (all 7 columns) holding three paragraphs: `${vulnTable}` · `${cells Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0,Initial Test=808080,Fixed=92D050,Not Fixed=C00000}` (the tagged DOCX adds `Retest=808080,Partially Fixed=FFC000`) · `${noIssuesText No vulnerabilities were identified during this assessment.}` | | | | | | |
| `${loop}` (keeps the column's auto-number) | `${vulnName}` | `${severity}` (cell fill `FAC701`) | `${cvssScore}` | `${assetLocation}` | `${affected_user}` | `${asmt_phase}` (static cell fill `808080`) |

6.2 Rules for the 2.9 table:

1. Faction repeats one row per finding. The five per-severity placeholder rows collapse into one `${loop}` row. Delete the four remaining placeholder rows.
2. The engine's cleanup removes exactly one `${…}` row (it stops as soon as the first matching paragraph belongs to a row that is already detached). So every config tag must share one row, placed above the `${loop}` row.

6.3 Section 5.x, one finding (`${fiBegin}` block). Top-level paragraphs, in order:

```
${fiBegin}
${fill Critical=C00000,High=FFC000,Medium=FFFF00,Low=00B050,Informational=00B0F0,Initial Test=808080,Fixed=92D050,Not Fixed=C00000}
                                    ← the tagged DOCX adds Retest=808080,Partially Fixed=FFC000
Heading 3:  ${vulnName}            ← Word numbering "5.1.%1" supplies 5.1.1, 5.1.2 …
[Word table]
   Severity | CVSS 3.1 | Status
   ${severity} (fill FAC701) | ${cvssScore} (hyperlink text: ${cvssString link}) | ${asmt_phase} (static fill 808080)
   Affected Assets | ${assetLocation}
   OWASP Top Ten   | ${category}            ← or ${owasp_top_ten}
   iSec Check List | ${isec_checklist_ref}
   Description     | ${desc}                ← the only text in the cell
   Impact          | ${impact_narrative}      ← RICH_TEXT UDF, the only text in the cell
   Recommendation  | ${rec}                 ← the only text in the cell
Heading 4:  Proof Of Concept
${details}
${pageBreak}
${fiEnd}
```

6.4 Rules for the 5.x block:

1. Keep one copy of the block. Delete "Finding 2" … "Finding 5".
2. Do not put `${noIssuesText}` in a block. The engine prints it per finding.

## 7. Changes made to the DOCX before upload

1. Remove the "Notes For Penetration Testers" page.
2. Replace every `{…}` placeholder with the `${…}` tag or UDF from sections 3 and 4. Faction ignores single-brace text.
3. Keep the native TOC. Do not add `${TOC}`.
4. Type all tags with spell-check off. Keep each block tag alone in its paragraph.
5. Charts (2.4, 2.5, 2.7) and checklists (4.1, 4.2) stay manual until the variables in `Faction_Backend_Changes_Needed.md`, section 3, exist.
6. Upload `iSec_WAPT_Template_Faction_UPLOAD.docx`, not the annotated master. Faction copies Word comments into every generated DOCX, so the file uploaded to Faction must carry none. The master `iSec_WAPT_Template_Faction.docx` keeps the "Faction Mapping" comments as documentation.
7. Verified on 2026-09-18 with a real generation in the local fork build (3 sample findings): no unresolved tags, TOC and 5.1.N numbering refreshed, colour maps applied, CVSS links resolved, 30-page PDF.
8. Template history after v5 (all applied to both files; scripts in the session scratchpad, results verified in Word and in Faction's LibreOffice output):
   - v6: the cover placeholder picture "[Insert Image Here]" became the `${clientImage}` tag in a text box narrowed to the placeholder's footprint; the footer placeholder pictures and the 1.0 logo paragraph were removed.
   - v7: the footer page number moved from a Word frame (dropped by LibreOffice, number invisible) into a page-anchored text box inside the pink tab.
   - v8: the footer placeholder box returned, holding a `${clientImage}` tag, once the engine could fill headers and footers (fork commit b77a81b); the cover tag got a fixed `width= height=` box.
   - v9 (current): the footer box grew to the iSec wordmark's width (`width=64 height=25`) and sits right after the separator bar, centred on the wordmark.
9. Verified on 2026-09-20 on the real Web template in the local fork build with the OneBank client: cover logo 3.68 × 1.43 cm at the placeholder position, footer logo 1.69 × 0.66 cm on every page from page 2, page numbers in the tab, no unresolved tags (`samples/Sample_Web_Report_v9*`).
