# Faction backend changes needed for the iSec WAPT template

Companion to `templates/web/isec-web-annotated.docx`.
Every item in sections 3, 4 and 6 is something the tagged template cannot get from Faction 2.0 today. The same text sits in a Word comment at that spot in the DOCX. Section 5 compares UDF names with the fork's MAPT template.
Nothing here can be built by misusing an existing variable.

## 1. Works now

1. Built-in variables, and the template sections they fill: `templates/web/MAPPING.md`, sections 2 and 3.
2. Layout of the 2.9 table and the 5.x block: same file, section 6.

## 2. UDFs to create and Faction configuration

Faction configuration before generating (no code change):

1. Terminology: keep the default severity labels `Critical`, `High`, `Medium`, `Low`, `Informational`. The template's `${cells}`/`${fill}` maps are keyed on these labels. "Info" is not used.
2. Vulnerability categories: the bootstrap OWASP Top 10 list feeds `${category}` ("A01 - Broken Access Control"). Rename them to the `A01:2025 - …` wording in Admin if the report must show the 2025 names.
3. Cover and Document Title print `${asmtClient} ${project_name} Penetration Testing Report`: the client name comes from the client record (organization), the project name from an assessment UDF. No assessment naming convention is needed.
4. UDFs: create them in the Report Designer. Variable names must match exactly. `templates/web/isec-web-udfs.json` holds the definitions; `Import-FactionUdfs.ps1` uploads them.

| Scope | Variable | Type | Options / default |
|---|---|---|---|
| Assessment | `summary1`, `summary2` | RICH_TEXT | Faction default fields ("Executive Summary", "Scope"). Not placed in this template. Add `${summary1}` / `${summary2}` paragraphs if an executive summary block is wanted. |
| Assessment | `report_version` | STRING | `1.0` |
| Assessment | `asmt_phase` | DROPDOWN | `Initial Test` only. Also fills every Status cell (2.9, 5.x), as in the MAPT template. This template is for initial tests; the retest report uses a separate template. |
| Assessment | `test_type` | DROPDOWN | `Black`, `Grey`, `White` |
| Assessment | `testing_hours` | DROPDOWN | `during working hours`, `after working hours, minimizing the impact on the server load, given the production environment's sensitivity` |
| Assessment | `project_name` | STRING | Application / project name for 2.1, 3.1, 3.1.1 and the 5.1 heading. Earlier draft name: `app_name`. Until `${asmtAppName}` exists. |
| Assessment | (removed) `client_name` | | The client name now comes from the client record as `${asmtClient}`. |
| Assessment | `app_url` | STRING | Hyperlink in 3.1.1, text in the 5.1 heading. Earlier draft name for the heading: `target_url`. Until `${asmtAppUrls_*}` exist. |
| Assessment | `app_version` | STRING | |
| Assessment | `environment` | DROPDOWN | `Production`, `Testing`, `UAT`, `Staging` |
| Assessment | `first_reviewer`, `second_reviewer`, `approver` | STRING | Until reviewer variables exist. |
| Assessment | `in_scope_functions`, `out_of_scope_functions`, `provided_credentials` | RICH_TEXT | Insert tables in the editor to keep the original column layout. |
| Assessment | `limitations` | RICH_TEXT | Default: "During this engagement, we encountered no limitations. All functionalities were tested as intended, and we were able to thoroughly assess the security posture of the system without any restrictions or issues." |
| Assessment | `recommendations` | RICH_TEXT | Default: the standard 6.0 bullet list. |
| Vulnerability | `affected_user` | STRING | |
| Vulnerability | `isec_checklist_ref` | STRING | Comma-separated checklist items. |
| Vulnerability | `impact_narrative` | RICH_TEXT | Narrative impact, not `${impact}`. |
| Vulnerability | `owasp_top_ten` | DROPDOWN | `A01:2025 - Broken Access Control`, … Only if `${category}` is not used. |

## 3. New variables to add

Engine: `DocxUtils` + `ReportData`.
Naming follows Faction's existing style: `asmt…` for assessment scope, camelCase, `_Lines/_Comma/_Bullets` list variants, `${xTable}` + `${loop}` for repeating rows, `FAC70n` sentinel colours.
Rows 3.8 and 3.12, and the `open-closed` chart in 3.15, serve the retest template. This template is initial test only.

| Row | Where in the template | Interim used now | Variable to add | Type | Example value |
|---|---|---|---|---|---|
| 3.1 | Cover title, 1.1 Document Title, 2.2 Disclaimer | Done: `${asmtClient}` (`main` commit 40047cc). The client is the assessment's own organization; an assessment created before its application was assigned to a client has none (a fallback to the application's client was added as fdc85b8 and reverted in 444fb5e on Kareem's request) | `${asmtSubOrg}` (sub-organization name) still open. | String | `Digital Banking` |
| 3.2 | 2.1, 3.1, 3.1.1 "Application Name" cell, 5.1 heading | STRING UDF `project_name` | `${asmtAppName}`: application name. | String | `Camunda Workflow Portal` |
| 3.3 | 3.1.1 "Application URL" cell (block tag for `_Lines`), 5.1 heading, 3.1.1 Testing Scope rows | STRING UDF `app_url` (`${app_url link}` hyperlink) | `${asmtAppUrls_Lines}` / `${asmtAppUrls_Comma}`: application URLs from the application record. `${engagementUrlTable}` + `${loop}` row with `${engUrl}`, `${engUrl link}`, `${engUrlDescription}`: loop over `assessment.engagementUrls`. | List of String; loop | `https://uat.example.com`; `https://uat.example.com / UAT front end` |
| 3.4 | 1.2 Diffusion List | Done: `${clientContactTable}` + `${loop}` row with `${contactName}`, `${contactTitle}`, `${contactEmail}` (`${contactEmail link}` for mailto), `${count}`; block forms `${clientContacts_Lines|_Bullets|_Comma}` (commit 40047cc). Data source: the client record's distribution list (commit 876a7a1), loaded into the report data by commit 488ca0f. | Loop | `Ahmed Ali / CISO / a.ali@bank.com` |
| 3.5 | 1.3 Document History: reviewer rows, reviewer date, "Author" / "Approver" rows, "End Date" for the final version | STRING UDFs `first_reviewer`, `second_reviewer`, `approver`; dates manual | `${asmtReviewers_Comma}` / `${asmtReviewers_Lines}` / `${asmtReviewers_Bullets}`: peer reviewers of the assessment. `${asmtReviewedAt [fmt]}`: date peer review completed. `${asmtEngagementManager}` / `${asmtEngagementManager_Email}`: engagement manager name / email. `${asmtCompleted [fmt]}`: completion date (not the planned end). | List of String; Date; String; Date | `Eng. Kareem Ahmed, Eng. Sara M.`; `04/05/2026`; `Eng. Mostafa Elguerdawi`; `02/05/2026` |
| 3.6 | 1.1 and 1.3 Version: "Penetration Testing Report v${reportVersion}" | STRING UDF `report_version` | `${reportVersion}`: report version derived from generation runs and retests (v1.0 initial, v2.0 first retest …). | String | `1.0` |
| 3.7 | 3.1.2 Scope Functions (block tag) | RICH_TEXT UDFs `in_scope_functions`, `out_of_scope_functions` | `${asmtScope}`: the assessment's own Scope field. | Rich text | `<p>Customer portal, all authenticated flows…</p>` |
| 3.8 | 2.9 Status column, 5.x Status cell; Document History for the retest date | `${asmt_phase}` (assessment DROPDOWN; a retest template will need a per-finding field) | `${findingStatus}`: engagement status derived from retests. `Initial Test` when no retest exists; `Fixed`, `Not Fixed` or `Partially Fixed` from the latest retest result. Add sentinel `FAC704` so `${cells Initial Test=808080,Fixed=92D050,Not Fixed=C00000}` colours it. `${retestedAt [fmt]}`: date of the latest retest. | String (enum); Date | `Not Fixed`; `10/05/2026` |
| 3.9 | 5.x Affected Assets cell (block tag) | `${assetLocation}` (one string) | `${assetLocations_Lines}` / `${assetLocations_Bullets}`: asset location split on newline, comma or semicolon into separate lines. | List of String | `https://app/path1`, `https://app/path2` |
| 3.10 | 5.x CVSS cell; column header "CVSS ${cvssVersion}" | `${cvssString link}` (link text becomes the vector) | `${cvssLink text="View CVSS Metrics"}`: calculator hyperlink to first.org with custom display text, or make `${cvssString link}` keep the surrounding hyperlink text. `${cvssVersion}`: `3.1` or `4.0` from the template scoring type. | Hyperlink; String | `9.0 (View CVSS Metrics)`; `3.1` |
| 3.11 | 5.x iSec Check List row | STRING vuln UDF `isec_checklist_ref` | `${vulnChecklistRefs_Comma}` / `_Lines`: checklist questions linked to this finding. Needs a new finding ↔ checklist-question relation, or a MULTI_SELECT UDF type. | List of String | `Authorization Bypass, IDOR` |
| 3.12 | 2.6 retest columns: "Not Fixed", "Fixed" and "Initial Test" per severity in a retest report | Not possible | `${riskCountOpen9..5}` (`${riskCountOpen9}` … `${riskCountOpen5}`): open findings per severity. `${riskCountClosed9..5}` (`${riskCountClosed9}` … `${riskCountClosed5}`): closed findings per severity. `${riskCountInitial9..5}` (`${riskCountInitial9}` …): findings carried forward from the original assessment, per severity. | Integer | `2`; `1`; `3` |
| 3.13 | 4.1 table: header row, one `${loop}` row, one row holding `${checklistTable isec-web-penetration-testing-checklist}`. Same for 4.2. | Manual (GAP), or the App Store checklist extension with a different layout | `${checklistTable <name>}` + `${loop}` row with `${checkNo}`, `${checkQuestion}`, `${checkDone}`, `${checkResult}`, `${checkComment}`: loop over checklist responses, rendered with the template's own row styling. `${checkDone}` prints `Done` when the result is Pass or Fail, `N/A` when N/A. Config paragraph `${checkLabels PASS=Not Vulnerable,FAIL=Vulnerable,NA=N/A}` in its own row maps Faction results to the words printed by `${checkResult}`. Sentinel `FAC705` on the result cell + `${cells Vulnerable=C00000,Not Vulnerable=92D050,N/A=D9D9D9}` colours the Status cell. | Loop; config; colour map | `12 / Authorization Bypass / Done / Vulnerable / see 5.1.3` |
| 3.14 | 2.4 / 2.5 text and chart data | Manual (GAP) | `${checklistPassCount <name>}`, `${checklistFailCount <name>}`, `${checklistNaCount <name>}`, `${checklistTotal <name>}`: counts per checklist. | Integer | `44 / 6 / 3 / 53` |
| 3.15 | 2.4, 2.5, 2.7 native Word charts; retest chart | Manual (GAP) | `${chartData severity}` / `${chartData checklist:<name>}` / `${chartData open-closed}`: marker paragraph placed immediately before a native Word chart. The engine rewrites the chart's cached values (`c:val`) and the embedded workbook, so the template's own chart styling is kept. `severity`: categories matched to the severity labels (2.7). `checklist:<name>`: series `Pass`, `Fail`, `N/A`, or the `${checkLabels}` words (2.4 / 2.5). `open-closed`: `Open` / `Closed` per severity (retest chart). | Series map (label → number) | `Critical=0, High=0, Medium=1, Low=5, Informational=1`; `Not Vulnerable=44, Vulnerable=6, N/A=3` |
| 3.16 | 2.1 during/after working-hours sentence; retest-only columns; per-phase wording; show 3.2 only when limitations were entered | DROPDOWN UDF `testing_hours` holding full clauses | `${if-eq var=value}` … `${end-if}`: keep the wrapped paragraphs only when a UDF or built-in equals the value, otherwise delete them. `${if-set var}` … `${end-if}`: keep them only when the value is non-empty. Markers are top-level paragraphs, like `${if-section}`. | Conditional block | `${if-eq asmt_phase=Retest}`; `${if-set limitations}` |
| 3.17 | Cover date, 1.1 Date of Issue | `${today MMMM d, yyyy}` → "May 11, 2026" | `ordinal` modifier: `${today MMMM d, yyyy ordinal}`. | Date | `May 11th, 2026` |
| 3.18 | 5.1 heading per target, in sectioned reports | Static heading `${project_name} - ${app_url}` | `${sectionName}`: inside `${fiBegin S}` blocks, the human-readable section name. | String | `Customer Portal` |
| 3.19 | 1.0 Document Control | none | `${asmtStatus}`: assessment status label. | String | `Completed` |
| 3.20 | Team table, if one is added | none | `${assessorTable}` + `${loop}` row with `${assessorName}`, `${assessorEmail}`: one row per assessor. | Loop | `Eng. Kareem Ahmed / k.ahmed@isec.com` |
| 3.21 | 5.x OWASP Top Ten row, only if categories are not already OWASP entries | `${category}`, or vuln DROPDOWN UDF `owasp_top_ten` | `${vulnOwaspCategory}`: OWASP Top 10 mapping when categories are organised as a taxonomy with a parent. | String | `A01:2025 - Broken Access Control` |
| 3.22 | Client logo on the cover and in the footer band, any client branding image | Done: `${clientImage <name>}` prints the image stored on the client record under that name (Clients > edit > Client images) in the body, headers and footers; `width=` / `height=` scale it, both together fit it into a fixed box (padded, centred, file margins trimmed) so every client's logo takes the same space; the paragraph is removed when the client has none (commits a81abd3, 40047cc, 488ca0f, b77a81b). | Image | `${clientImage logo width=139 height=54}` (cover), `${clientImage logo width=64 height=25}` (footer) |

## 4. Engine fixes

| Row | Issue | Where it bites | Change |
|---|---|---|---|
| 4.1 | `${asmtAppId}` (docs) vs `${asmtAppid}` (DOCX engine keyword list). | Any DOCX using the documented spelling gets no value. | Accept both spellings in `DocxUtils.KEYWORDS`. |
| 4.2 | `${noIssuesText …}` inside a `${fiBegin}` block is read but not stripped. | It prints once per finding. | Strip it like `${color}` / `${fill}` / `${custom-fields}`. The tagged template omits it from the block and uses the default text. |
| 4.3 | `${vulnTable}` cleanup removes only one configuration row. `indexOfRow` walks the paragraph list captured before any row was removed and returns on the first `${…}` paragraph. Once that row is gone (or when it is the `${loop}` row, which is removed first), `indexOf` yields -1 and the `while` loop stops. | A second config row, or any config row below the `${loop}` row, is printed in the report. | Skip detached rows in `indexOfRow`, or rebuild the paragraph list each pass. Workaround in the tagged template: all three config tags (`${vulnTable}`, `${cells}`, `${noIssuesText}`) are paragraphs of one merged row placed above the `${loop}` row. |
| 4.4 | `${count}` is not resolved inside `${fiBegin}` blocks (only in `${vulnTable}` rows). | Finding headings cannot be numbered by Faction. | Add `${count}` to `setFindings`. The tagged template uses a Word numbering definition (`5.1.%1`) instead. |
| 4.5 | Date tags are not replaced in headers/footers. | Any `${today}` in a footer stays literal. | Run `replaceDateVariable` over header/footer parts too. |
| 4.6 | Report Sections were Enterprise-only. | Per-target grouping (5.1, 5.2 …) was unavailable. | Done in the fork: commit 2d62270 (2026-09-18) makes `CommunityEditionPolicy.enabled()` return `true` for every feature (sections, encrypted PDF, custom roles, external owners and more). Still open: a finding filed under a section the template has no block for is dropped silently; the fork's untracked `TO_DO_PLAN.md` (in git history at cf74795) describes the preflight validation to add. |
| 4.7 | `replaceHeaderAndFooter` uses `getHeaderFooterPolicy()` of one section only. | Multi-section templates (cover / TOC / body) with different headers get partial replacement. | Iterate all sections' header/footer parts. |
| 4.8 | Findings block insertion point ignores how many blocks a rich-text field expands to (`setFindings`, `begin++` per template paragraph, then `replaceHTML` swaps one paragraph for N). | A Proof of Concept with several steps, a code block or a table pushes the next finding inside the previous one. | Fixed on `main` (commit 6337a59): advance `begin` by the size change of the body after `replaceHTML`. Regression test `DocxUtilsFindingsBlockOrderTest`. |
| 4.9 | `clampToPageWidth` measures imported tables against the page, never against the cell they land in. | A `width:100%` table typed into a finding's Description runs off the right edge of the page. | Clamp to the containing cell's width when the rich text is inserted into a `w:tc`. Interim: the template CSS sets `.desc table, .rec table {width:400px}` (`shared/isec-report.css`). |
| 4.10 | The XHTML importer drops CSS3 structural pseudo-classes. | `tr:nth-child(even)` zebra striping never renders. | Post-import pass that shades alternate rows, or explicit row classes from the editor. |
| 4.11 | `word-break` is not mapped to `w:wordWrap`. | `div{word-break:break-all}` in the template CSS does nothing. | Map `word-break`/`overflow-wrap` to run or paragraph properties, or document it as unsupported. |
| 4.12 | The generated DOCX is round-tripped through LibreOffice (`DocxReportGenerationService.normalizeDocxViaCli` / UNO). LibreOffice writes an empty `<w:tcBorders/>` on every cell of a table styled `TableGridLight`, and Word reads that as "no border on any edge". | Every `TableGridLight` table (1.1, 1.2, 1.3, 2.9, 3.1.x, 4.x, the finding blocks, the appendix) showed no grid when the generated DOCX was opened in Word; the Faction PDF was unaffected. 448 of 589 cells came back empty. | Cured in the template, the same way as the MAPT template (fork commit 643ec3e): template v5 restyles all 14 `TableGridLight` tables to `TableGrid` and writes explicit top/left/bottom/right borders, single 0.5pt BFBFBF, on every cell that had none (364 cells) and completes the undeclared edges of 21 partially bordered cells (merged cells in 1.3, 2.8.1 and 3.3) that used to inherit them from the style; edges declared nil (the 2.8.3 axis labels) and the tables that were TableGrid all along are untouched. Verified in Word and LibreOffice: 4 empty cells remain, all created by the engine, not the template: the "no contacts" row `checkContactTables` builds without copying the cell borders, and 3 cells of an HTML table inside a rich-text field. |

| 4.13 | Faction's LibreOffice pass drops Word frames (`w:framePr`). The template's footer page number sat in such a frame inside the pink tab and came out invisible in every generated report. | Template-side cure applied in v7: the PAGE field moved into a page-anchored text box at the tab's position; LibreOffice keeps text boxes. Still dropped by the same pass: the front matter's Roman page numbering (`pgNumType upperRoman`), which prints 1, 2, 3 instead of I, II, III. |

## 5. UDF names: unified with the fork's MAPT template

The fork ships `report-templates/templates/mobile/isec-mobile-upload.docx` (commit 173bb9e). Its names are the reference. The Web template was renamed on 2026-09-18 so shared information uses the same variable in both.

Renamed in the Web template:

| Information | Old Web name | Name now (= MAPT) |
|---|---|---|
| Client name | `client_full_name` | `${asmtClient}` built-in from the client record. The Web template dropped the `client_name` UDF; MAPT still carries its own `client_name` UDF. |
| Initial test / retest | `test_phase` | `asmt_phase` |
| Box type | `box_type` | `test_type` |
| Status cell in 2.9 and 5.x | vulnerability UDF `finding_status` + `${custom-fields}` | `${asmt_phase}` (assessment), static grey fill |
| iSec checklist row | `isec_checklist` | `isec_checklist_ref` |
| Impact narrative | `impact_details` | `impact_narrative` |
| 6.0 body | `general_recommendations` | `recommendations` (name from the MAPT spec) |
| Cover and 1.1 title | `${asmtName}` | `${asmtClient} ${project_name}` |

Already identical: `project_name`, `report_version`, `limitations`, `summary1`, `summary2`.

Intentionally unchanged:

| Variable | Why |
|---|---|
| `${category}` for the OWASP Top Ten row (Web) vs `owasp_category` UDF (MAPT) | Web's category list is the OWASP Top 10, so the built-in is correct and avoids double entry. MAPT needs OWASP Mobile and API lists, which its categories do not hold. |
| `affected_user`, `app_url`, `app_version`, `environment`, `testing_hours`, `first_reviewer`, `second_reviewer`, `approver`, `in_scope_functions`, `out_of_scope_functions`, `provided_credentials` | Web-only sections; MAPT leaves them static. |
| `app_variant` (MAPT) | Mobile-only (Shielded / Unshielded). |
| `${loop}` + Word auto-number (Web) vs `${loop}${count}` (MAPT) | Mechanism for the `#` column, not a variable name. |
| MAPT sections `Android_Application`, `iOS_Application`, `API` | Mobile-only grouping; Web renders one block. |

## 6. Parts that stay manual until then

Until the open rows of section 3 exist, fill these by hand:

1. 1.3 reviewer dates.
2. 2.4 / 2.5 / 2.7 charts.
3. 4.1 / 4.2 checklist tables.
4. Per-target 5.1 groups (Enterprise sections).

Each carries a Word comment authored "Faction Mapping" in the tagged DOCX.
