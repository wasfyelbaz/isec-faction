# iSec MAPT Report Template — Faction 2 Conversion Spec

Changes required to make `1._iSec_MAPT_Template.docx` render correctly when uploaded to
Faction 2 as a DOCX report template.

**Source of truth:** every variable below was verified against this repository's renderer,
`backend/src/main/java/com/faction/clientportal/util/reporting/DocxUtils.java`, not only
against <https://docs.factionsecurity.com/reporting/docx-templates/>. Where the docs and the
code disagree, the code wins — it is what runs.

- **Input:** `1._iSec_MAPT_Template.docx` (unmodified, keep as reference)
- **Output:** `2._iSec_MAPT_Template_Faction.docx`

---

## Status

| Step | Scope | State |
|---|---|---|
| 1 | Findings sections — §2.10.x overview tables, §5.1–5.3 finding blocks | **Applied** |
| 2a | Finding-level UDF placeholders (`owasp_category`, `isec_checklist_ref`, `impact_narrative`, `asmt_phase`) | **Applied** |
| 2b | Cover page, §1.1, and the `{…}` fill-in markers in retained prose | **Applied** |
| 2c | Assessment prose UDFs — replacing the §2.1/2.2/2.3/3.2/6.0 *bodies* | **Not applied — needs a decision, see below** |
| 3 | §1.2, §1.3, §2.7, §2.8, §3.1.x, §4.x left as static text | **Deliberately unchanged** |
| 4 | Core code changes (`${riskCountN Section}`, etc.) | **Not started** |

Whatever is applied, the fields themselves are configuration and must be created in Faction
before the first render — see [§B](#b-user-defined-fields-to-create).

**On 2c.** §2.1, §2.2, §2.3, §3.2 and §6.0 currently hold several paragraphs of iSec
boilerplate. Wiring them to `${project_objective}`, `${disclaimer}`, `${methodology}`,
`${limitations}` and `${recommendations}` means *deleting that prose from the DOCX* and
re-typing it as each field's default value in the Faction UI. That is the right move if
assessors should be able to tailor the wording per engagement, and the wrong move if the
boilerplate is meant to be fixed — in which case leave it as static text and create only
`limitations` (the one section that genuinely varies). This was left unapplied because it
destroys content and the answer depends on how your team works, not on what Faction supports.

---

## A. Faction configuration prerequisites

### A.1 Report sections

Create exactly these three sections on the report template, **in this order**. The names
matter: the template tags are derived from them by replacing spaces with underscores
(`DocxUtils.sectionVariable`, `DocxUtils.java:128`).

| Section name | Tag token used in the DOCX |
|---|---|
| `Android Application` | `Android_Application` |
| `iOS Application` | `iOS_Application` |
| `API` | `API` |

Every finding must then be filed under one of the three. Anything left unfiled falls into the
implicit `Default` section, which this template has no block for — those findings would be
silently absent from the report. **Check for unfiled findings before generating.**

### A.2 Severity terminology

The `${cells …}` colour maps in the template key off the *display* severity string. They are
written for Faction's defaults:

`Critical`, `High`, `Medium`, `Low`, `Informational`

If your installation renames severities (Admin → Terminology), update the `${cells …}` rows in
the template to match, or the colour lookup silently falls back to white. Note this affects
colours only — `${riskCountN}` and `{[asmtCRITICAL]}` key off the stable enum
(`ReportData.java:127`) and survive renaming.

---

## B. User-defined fields to create

Admin → Report Templates → the template → Fields. `variableName` must be alphanumeric plus
underscores (`UserDefinedField.java:32`).

### B.1 Assessment scope

| variableName | Display name | Type | Used for | Appears in |
|---|---|---|---|---|
| `client_name` | Client Name | STRING | Full legal client name | Cover, §1.1, §2.2 |
| `project_name` | Project Name | STRING | Engagement/project name | Cover, §1.1, §2.1, §3.1 |
| `report_version` | Report Version | STRING | Document revision, e.g. `1.0` | Cover, §1.1 |
| `asmt_phase` | Assessment Phase | DROPDOWN (`Initial Test`, `Retest`) | Engagement phase | §2.10.x, §5.x status cells |
| `test_type` | Test Type | DROPDOWN (`Black`, `Grey`, `White`) | Box type | §2.1, §3.1 |
| `project_objective` | Project Objective | RICH_TEXT | §2.1 body | §2.1 |
| `disclaimer` | Disclaimer | RICH_TEXT | §2.2 body | §2.2 |
| `methodology` | Assessment Methodology | RICH_TEXT | §2.3 body | §2.3 |
| `limitations` | Testing Constraints and Limitations | RICH_TEXT | §3.2 body | §3.2 |
| `recommendations` | Overall Recommendations | RICH_TEXT | §6.0 body | §6.0 |

The first seven are referenced by the template now and **must exist before the first render**,
or their tags print literally. The five `RICH_TEXT` ones at the bottom are only needed if you
take up step 2c. §1.1's Classification (`Confidential`) and Description were left as static
text — they do not vary per engagement, so a field for them would be one more thing to fill in
for no gain.

`asmt_phase` is deliberately assessment-scoped, not vulnerability-scoped: it describes the
engagement, not the finding. It resolves correctly inside cloned finding rows because
`replaceAssessment` runs over the whole document *after* the rows are cloned
(`DocxUtils.java:731`), and the table's leftover-tag cleanup only scans the template's
original paragraphs (`DocxUtils.java:410`), so a cloned row carrying `${asmt_phase}` is not
mistaken for a config row and removed.

### B.2 Vulnerability scope

| variableName | Display name | Type | Used for |
|---|---|---|---|
| `owasp_category` | OWASP Top Ten | STRING | §5.x "OWASP Top Ten" / "OWASP API Top Ten" row |
| `isec_checklist_ref` | iSec Check List | STRING | §5.x "iSec Check List" row |
| `impact_narrative` | Impact (narrative) | RICH_TEXT | §5.x "Impact" row |
| `app_variant` | Application Variant | DROPDOWN (`Shielded`, `Unshielded`) | the `(Un/Shielded)` qualifier |

A `RICH_TEXT` field only expands when its placeholder is **alone in its paragraph or cell**
(`DocxUtils.java:747`). `impact_narrative` therefore gets a cell to itself. `STRING` and
`DROPDOWN` substitute inline anywhere.

---

## C. Template edits

### C.0 Cover page, §1.1, and inline fill-in markers

The template marks its fill-in points with single-brace text — `{Month DayNN, Year}`,
`{ Client Name + Project Name }`, `{Initial Test/Retest}`. Each one that has a variable behind
it was replaced **in place**, leaving the surrounding prose and per-run formatting untouched:

| Template marker | Replaced with | Appears in |
|---|---|---|
| `{Month DayNN, Year}` | `${today MMMM d, yyyy}` | Cover, §1.1 Date of Issue |
| `{ Client Name + Project Name }` | `${client_name} ${project_name}` | Cover, §1.1 Document Title |
| `{ Client Name With No Abbreviations }` | `${client_name}` | §2.2 Disclaimer |
| `{ Project Name }` | `${project_name}` | §2.1, §3.1 |
| `{Initial Test/Retest}` | `${asmt_phase}` | §2.1, §3.1 |
| `{Type}` / `{type}` | `${test_type}` | §2.1, §3.1 |
| `{x.x}` | `${report_version}` | Cover, §1.1 |

Fourteen replacements in total. Two mechanics made this less trivial than it looks:

- **The cover page is a content control containing three text boxes, and every text box exists
  twice** — once under `mc:Choice` and once under `mc:Fallback`. Word renders the Choice branch;
  the Fallback is for readers that do not understand the Choice. Both branches must carry
  identical text, or the cover changes depending on what opens the file. Editing only what Word
  shows you is the easy mistake here.
- **The markers are split across runs** — `{ Client Name + Project Name }` spans eight of them,
  which is why searching the raw XML for that string finds nothing. The replacement writes into
  the first run the marker touches and clears the remainder, rather than collapsing the
  paragraph into one run, so the cover's letter-spacing and the prose's highlighting survive.
  Faction itself copes with split runs at render time — docx4j's `VariablePrepare.prepare`
  merges them first (`DocxUtils.java:706`) — so a `${…}` typed by hand into Word is safe even
  if Word fragments it.

**Left as literal text on purpose:** the title line `Mobile Application Penetration Testing
Report`. `${asmtType}` would render whatever the installation names that assessment type, which
may or may not be that phrase; this is the MAPT template, so the title is its identity rather
than a variable. Also `{during/after}` in §2.1 — a wording choice, not data.

**Still literal, because there is no variable:** `{Author}`, `{First Reviewer}`,
`{Second Reviewer}`, `{Approver}` and `{Contact_Name}` / `{Contact_Title}` / `{Contact_Email}`
in §1.2–§1.3. These are the repeating-table gap in [§E](#e-cannot-be-expressed-in-faction-2--left-as-static-text).

### C.1 §2.10.1 / §2.10.2 / §2.10.3 — Findings Overview tables

Each table previously held a header row plus five hardcoded dummy rows. Each is now a header
row, one config row, and one repeating row.

**Config row** (single cell spanning the table, two paragraphs, removed at render time):

```
${vulnTable Android_Application}
${cells Critical=C00000,High=FFC000,Medium=F8F200,Low=00B050,Informational=00B0F0}
```

**Repeating row**, §2.10.1 and §2.10.2:

| # | Vulnerability | Risk | CVSS 3.1 | Status |
|---|---|---|---|---|
| `${loop}${count}` | `${vulnName}` | `${severity}` | `${cvssScore}` | `${asmt_phase}` |

**Repeating row**, §2.10.3 (API — has the extra Affected URL column):

| # | Vulnerability | Risk | CVSS 3.1 | Affected URL | Status |
|---|---|---|---|---|---|
| `${loop}${count}` | `${vulnName}` | `${severity}` | `${cvssScore}` | `${assetLocation}` | `${asmt_phase}` |

Three mechanics that are easy to get wrong here:

- **`${vulnTable …}` goes *inside* the table, in a cell — not in a paragraph above it.**
  `checkTables` only searches paragraphs belonging to the table (`DocxUtils.java:410`), so a
  marker placed above the table is never found and the table is skipped entirely.
- **`${loop}` must start its paragraph.** The row lookup uses `startsWith`
  (`DocxUtils.java:2149`), hence `${loop}${count}` and not `${count}${loop}`. `${loop}`
  renders as empty.
- **The repeating row must be the table's last row.** Cloned rows are appended to the end of
  the table (`DocxUtils.java:607`), so anything below the template row would end up above the
  findings.

The Risk cell's fill is set to the sentinel `FAC701`, which Faction swaps for the colour the
`${cells …}` row maps that severity to.

`${count}` is available here because this is table mode. It is **not** implemented in block
mode — see C.2.

### C.2 §5.1 / §5.2 / §5.3 — Vulnerability Findings

Each of the three groups previously contained five hand-copied finding blocks (fifteen in
total). Each group is now one block that repeats per finding:

```
${if-section Android_Application}
  5.1 Android Application Findings          ← Heading 2, unchanged
  ${fiBegin Android_Application}
    ${fill Critical=C00000,High=FFC000,…}   ← own paragraph, see warning below
    ${vulnName}                             ← Heading 3
    [finding detail table]
    Proof Of Concept                        ← Heading 4
    ${details}
    ${pageBreak}
  ${fiEnd Android_Application}
${end-section Android_Application}
```

`${if-section …}` … `${end-section …}` deletes everything between the markers — including the
`5.1 …` heading — when the section has no findings (`DocxUtils.java:181`), so an engagement
with no iOS findings produces no empty iOS chapter.

**Finding detail table** (§5.1 and §5.2):

| Left cell | Right cell |
|---|---|
| Severity / CVSS 3.1 / Status | `${severity}` · `${cvssScore} (${cvssString link})` · `${asmt_phase}` |
| OWASP Top Ten | `${owasp_category}` |
| iSec Check List | `${isec_checklist_ref}` |
| Description | `${desc}` |
| Impact | `${impact_narrative}` |
| Recommendation | `${rec}` |

§5.3 (API) is identical plus an **Affected Assets** row holding `${assetLocation}`.

The Severity cell carries the `FAC701` sentinel fill, which Faction swaps for the colour the
`${fill …}` map gives that severity.

> **Do not put the `${fill …}` map in a row of the finding table.** Block mode uses a different
> stripping mechanism from table mode: it blanks the **entire marshalled node** when that node
> contains `${fill`, `${color` or `${custom-fields` (`DocxUtils.java:932`). The finding table is
> one node, so a config row inside it deletes the whole table from every finding — the template
> still uploads and renders, it just silently produces findings with no detail table. The map
> therefore lives in its own paragraph just after `${fiBegin …}`, which is blanked harmlessly.
>
> Table mode (§C.1) is the opposite: there config tags go *inside* the table, because it removes
> config **rows** by index (`DocxUtils.java:655`). Same idea, incompatible placement — this is
> the single easiest thing to get wrong when hand-editing this template.

Block mode also spells the tag `${fill …}`; table mode spells it `${cells …}`
(`DocxUtils.java:437` vs `DocxUtils.java:832`). Same effect, different word.

#### Two deliberate losses in this table

**"View CVSS Metrics" wording.** `replaceHyperlink` overwrites the hyperlink's *display text*
with the CVSS vector string (`DocxUtils.java:1977`). The cell renders as
`9.1 (CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H)`, linked to the FIRST calculator with the
vector prefilled. The link works; the friendly label cannot be kept.

**Manual heading numbers.** `5.1.1`, `5.1.2`, … cannot survive a repeating block: the heading
is now `${vulnName}` alone. `${count}` is not available in block mode — compare the table-mode
substitutions at `DocxUtils.java:503` with the block-mode set at `DocxUtils.java:880`, which
has no `${count}`. To get numbering back, attach Word multilevel list numbering to the
Heading 3 style rather than typing numbers; Word then numbers the repeated headings correctly
after a field update (Ctrl+A, F9).

### C.3 Proof of Concept

The literal `Step 1. / Step 2. / Step 3.` placeholders are replaced by a single `${details}`
paragraph. Screenshots pasted into the finding's Details field in Faction are embedded
automatically — inline images are converted to data URIs at render time
(`DocxUtils.java:1189`).

---

## D. Mappings deliberately NOT made

Each of these has a Faction variable that looks close enough to be tempting and means
something else. Using it would produce a report that renders but is wrong.

| Template field | Tempting variable | Why it is wrong | Used instead |
|---|---|---|---|
| §5.x **Impact** row | `${impact}` | `${impact}` is a *rating value* (`High`/`Medium`/`Low`) — the same field the `FAC703` colour sentinel keys off. Your row is a narrative paragraph. | `${impact_narrative}` UDF |
| §5.x / §2.10.x **Status** | `${remediationStatus}` | Emits only the literals `Open` or `Closed` (`DocxUtils.java:525`), a property of the finding. Your column holds `Initial Test`/`Retest`, a property of the engagement. | `${asmt_phase}` UDF |
| §5.x **OWASP Top Ten** | `${category}` | `${category}` is the Faction vulnerability-category name. It equals the OWASP entry only if the taxonomy is built that way, and the template needs OWASP *and* the iSec checklist reference on the same finding — two values, one variable. | `${owasp_category}` UDF |
| §1.3 Author / Reviewer / Approver | `${asmtAssessor*}` | Emits the testing team, flat. The rows carry distinct workflow roles each with its own date. | nothing — see E |
| `(Un/Shielded)` in finding titles | part of `${vulnName}` | A build-variant qualifier, not part of the finding name; folding it in corrupts the name everywhere else it appears. | `${app_variant}` UDF |

Also avoid `${asmtTeam}` (always empty in Faction 2 — `DocxUtils.java:1069`) and
`${asmtAccessKey}` (just repeats the assessment ID — `DocxUtils.java:1071`).

---

## E. Cannot be expressed in Faction 2 — left as static text

These sections are **unchanged on purpose** and must be filled in by hand after generation. An
unresolved `${…}` is printed literally into the delivered DOCX, so a speculative tag would be
worse than static text.

| Section | Blocker |
|---|---|
| §1.2 Diffusion List | Repeating contact rows. Faction can only iterate over findings. |
| §1.3 Document History | Repeating version rows; no revision-history model. |
| §2.4 / §2.5 / §2.6 checklist summaries | No checklist model in core. |
| §2.7 Findings Distribution | Needs **per-section** severity counts. `${riskCountN}` is global-only — the count path calls the section-less `getFilteredVulns()` (`DocxUtils.java:1212`). |
| §2.8 Findings Distribution Chart | `${faction-bar-chart}` is resolved by the extension chain (`DocxUtils.java:670`); no such extension ships in core. |
| §3.1.1 Testing Scope | Per-application rows. `APPLICATION`-scoped UDFs never reach the renderer — the field map is built only from the assessment's own definitions (`DocxReportGenerationService.java:386`). |
| §3.1.2 Scope Functions | Repeating rows, no loop. |
| §3.1.3 Provided Credentials | Repeating rows, no loop. |
| §4.1 / §4.2 / §4.3 checklists | `${checklist-owasp-top-10 …}` is extension-only (`DocxUtils.java:692`). |

The common cause is one limitation: **Faction can only iterate over findings.** Six of these
nine are tables that repeat over something else.

---

## F. Proposed additions to Faction

Ordered by cost-to-value. Group B is the one worth doing first.

### Group A — scalars

| Variable | Purpose | Type | Example | Template use |
|---|---|---|---|---|
| `${clientName}` | Client/org name; currently unreachable from a template | String | `Acme Financial Group` | Cover, §1.1, §2.2 |
| `${asmtPhase}` | Engagement phase, distinct from finding status | Enum: `Initial Test`/`Retest` | `Initial Test` | §2.10.x, §5.x |
| `${reportVersion}` | Document revision | String | `1.0` | Cover, §1.1 |
| `${appName}` / `${appVersion}` / `${appEnvironment}` | Per-application scope facts | String | `Acme Mobile 3.4.1`, `UAT` | §3.1.1 |

These are currently worked around with assessment UDFs (§B.1). Promoting `clientName` to a
first-class variable would remove per-template setup for every client Faction has.

### Group B — section-scoped counts

| Variable | Purpose | Type | Example | Template use |
|---|---|---|---|---|
| `${riskCount9 Section_Name}` | Severity count within one section | Integer | `${riskCount8 Android_Application}` → `2` | §2.7 matrix |
| `${riskTotal Section_Name}` | All findings in a section | Integer | `3` | §2.7 row totals |

Smallest change, largest payoff. `getFilteredVulns(String section)` already exists
(`DocxUtils.java:153`); the counting path (`DocxUtils.java:1114`) simply calls the no-arg
overload. Make `getVulnMap()` section-aware and extend the tag parser to accept an optional
section argument — the same parse `${vulnTable X}` already performs. Roughly a day, and it
makes §2.7 render.

### Group C — generic repeating tables

| Variable | Purpose | Type | Example | Template use |
|---|---|---|---|---|
| `${tableBegin <id>}` … `${tableEnd <id>}` | Iterate a named assessment-level list field | Block markers | wraps one `<w:tr>` | §1.2, §1.3, §3.1.1, §3.1.2, §3.1.3 |
| `${row.<column>}` | A column of the current row | String | `${row.contact_email}` → `ciso@acme.com` | inside the above |
| `${rowCount}` | 1-based counter | Integer | `1` | `#` columns |

Needs a `TABLE` value in `FieldType`, a column schema on `UserDefinedField`, and a grid editor
in the UI. The expensive one — but it alone closes five gaps and generalises well past this
template.

### Group D — checklists

| Variable | Purpose | Type | Example | Template use |
|---|---|---|---|---|
| `${checklistBegin <id>}` … `${checklistEnd <id>}` | Iterate a checklist's items | Block markers | `${checklistBegin owasp-mobile-top-10}` | §4.1, §4.2, §4.3 |
| `${item.ref}` / `${item.text}` / `${item.status}` / `${item.comment}` | Current item's fields | String | `M3`, `Insecure Authentication`, `Not Vulnerable` | inside the above |
| `${checklistCount <id> <status>}` | Items at a status, for the summaries | Integer | `${checklistCount owasp-mobile-top-10 Vulnerable}` → `2` | §2.4, §2.5, §2.6 |

Faction already anticipates checklists through the extension token, so the cheapest route is a
`ReportManager` extension JAR rather than a core change — that gets §4.x working without
diverging from the upstream this fork tracks.

---

## G. Verifying a render

1. Upload `2._iSec_MAPT_Template_Faction.docx` as a report template.
2. Create the sections from §A.1 and the fields from §B.
3. Create an assessment with at least one finding in **each** of the three sections, and at
   least one section left empty, to exercise both the repeat and the `${if-section}` removal.
4. Generate, then check:
   - no literal `${…}` text anywhere in the output;
   - §2.10.x tables have one row per finding and no leftover config row;
   - the empty section's heading is gone entirely;
   - severity cells are coloured, not white (white means the `${cells}`/`${fill}` keys do not
     match your severity display names — see §A.2);
   - each finding starts on its own page.
5. Open in Word, Ctrl+A, F9 to refresh the table of contents and heading numbering.
