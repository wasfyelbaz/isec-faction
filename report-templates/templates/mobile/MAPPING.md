# iSec MAPT template — mapping

How the iSec Mobile Application Penetration Testing template maps onto Faction 2: what each
section became, what was deliberately left alone, what Faction cannot express, and what would
have to be built for the rest.

Cross-template asks live in [`../../BACKEND_GAPS.md`](../../BACKEND_GAPS.md); section F below is
this template's own analysis and overlaps it in places.

**Source of truth:** every variable below was verified against this repository's renderer,
`backend/src/main/java/com/faction/clientportal/util/reporting/DocxUtils.java`, not only
against <https://docs.factionsecurity.com/reporting/docx-templates/>. Where the docs and the
code disagree, the code wins — it is what runs.

- **Input:** `report-templates/templates/mobile/isec-mobile-original.docx` (unmodified, keep as reference)
- **Output:** `report-templates/templates/mobile/isec-mobile-upload.docx`

---

## Status

| Step | Scope | State |
|---|---|---|
| 1 | Findings sections — §2.10.x overview tables, §5.1–5.3 finding blocks | **Applied** |
| 2a | Finding-level UDF placeholders (`owasp_category`, `isec_checklist_ref`, `impact_narrative`, `asmt_phase`) | **Applied** |
| 2b | Cover page, §1.1, and the `{…}` fill-in markers in retained prose | **Applied** |
| 2c | Assessment prose UDFs — `${limitations}` (§3.2) and `${recommendations}` (§6.0) | **Applied** |
| 3 | §1.2, §1.3, §2.7, §2.8, §3.1.x, §4.x left as static text | **Deliberately unchanged** |
| 4 | Core code changes (`${riskCountN Section}`, etc.) | **Not started.** The bar chart and checklists of §E were closed separately, in-process rather than by new variables. |

Whatever is applied, the fields themselves are configuration and must be created in Faction
before the first render — see [§B](#b-user-defined-fields-to-create).

**On 2c.** §2.1 Project Objective, §2.2 Disclaimer and §2.3 Methodology stay as **static text**.
That prose is fixed in every iSec report, so making it a field would only add something to fill
in that never changes.

§3.2 Testing Constraints and Limitations and §6.0 Recommendations do vary per engagement, and
are now fields. The prose they used to hold has been removed from the DOCX and must be entered
as each field's **default value** in Faction — otherwise those two sections render empty. The
exact defaults are in [§B.3](#b3-default-values-for-the-rich-text-fields); set them when you
create the fields, not afterwards.

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
| `working_hours` | Working Hours | DROPDOWN (see below) | When testing was run, and the caveat that follows from it | §2.1 |
| `limitations` | Testing Constraints and Limitations | RICH_TEXT | §3.2 body — **needs a default, see §B.3** | §3.2 |
| `recommendations` | Overall Recommendations | RICH_TEXT | §6.0 body — **needs a default, see §B.3** | §6.0 |

**All eight are referenced by the template and must exist before the first render**, or their
tags print literally into the delivered report. The two `RICH_TEXT` ones additionally need
their default values set, or §3.2 and §6.0 render as empty sections under a live heading.

`working_hours` carries the whole clause, not just the word, because the two cases do not
differ by one word — the out-of-hours case brings a caveat with it. Its two dropdown options
are, verbatim:

```
during working hours.
after working hours, minimizing the impact on the server load, given the production environment's sensitivity.
```

The sentence in §2.1 reads `… the penetration testing engagement was initiated ${working_hours}`,
so the option supplies its own closing full stop. A `DROPDOWN` substitutes inline, which is
what lets it sit mid-paragraph; a `RICH_TEXT` field would not, since those only expand when
alone in their paragraph.

§1.1's Classification (`Confidential`) and Description are left as static text — they do not
vary per engagement, so a field for them would be one more thing to fill in for no gain. §2.1's
Project Objective, §2.2's Disclaimer and §2.3's Methodology are fixed iSec boilerplate in every
report and are deliberately **not** fields, for the same reason.

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

### B.3 Default values for the rich-text fields

This prose used to live in the DOCX. It now lives in the field defaults, so it must be entered
when the field is created — a `RICH_TEXT` field with no default renders nothing, leaving a
heading with an empty body.

**`limitations`** — the no-limitations wording, which is the common case. Replace it on any
engagement that did hit constraints:

> During this engagement, no limitations were encountered. All functionalities were tested as
> intended, and were able to thoroughly assess the security posture of the system without any
> restrictions or issues.

**`recommendations`** — the five standing recommendations, verbatim from the template:

> The following actions are recommended for you to improve the current security.
>
> - Cover all the findings stated in this report.
> - Unless a resource is intended to be publicly accessible, deny access by default.
> - Remove all files which can disclose any information regarding the application.
> - Update the outdated versions and libraries.
> - Server Type disclosure, Server Header should be removed from the server responses.

Type these into the rich-text editor rather than pasting HTML — the editor stores HTML itself,
and `RichTextEditor` is the only editor the frontend uses. If you do want the markup, it is:

```html
<p>The following actions are recommended for you to improve the current security.</p>
<ul>
  <li>Cover all the findings stated in this report.</li>
  <li>Unless a resource is intended to be publicly accessible, deny access by default.</li>
  <li>Remove all files which can disclose any information regarding the application.</li>
  <li>Update the outdated versions and libraries.</li>
  <li>Server Type disclosure, Server Header should be removed from the server responses.</li>
</ul>
```

`<ul>` is correct: the original list was a Word bullet list (`numFmt: bullet`), not a numbered
one, so it should not come back as `<ol>`.

The guidance text that stood above §3.2's placeholder — the long "*Here the assessor should
mention the limitations … (Explain it and use ChatJPT)*" note — was **removed, not migrated**.
It was an instruction to the author, not report content, and it has no place in a field that
renders straight into a client deliverable. Keep it in your own writing guide if it is still
useful.

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
| `{during/after} working hours{./, minimizing …}.` | `${working_hours}` | §2.1 |

Fifteen replacements in total. Two mechanics made this less trivial than it looks:

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
than a variable.

The `{during/after}` marker in §2.1 is the one case where the whole clause became the variable
rather than the word inside the braces. The template wrote it as two independent choices —
`{during/after} working hours{./, minimizing the impact on the server load, …}` — but they are
not independent: "after" always brings the server-load caveat and "during" never does. Two
fields would let an assessor produce "during working hours, minimizing the impact on the server
load", which is nonsense. One `DROPDOWN` holding both complete clauses cannot.

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
| ~~§2.8 Findings Distribution Chart~~ | **No longer a gap.** `${faction-bar-chart}` is rendered in-process by `SeverityBarChartRenderer`; the App Store extension chain it used to depend on has been removed from this fork. Colours and size are configured per template in the Report Designer. |
| §3.1.1 Testing Scope | Per-application rows. `APPLICATION`-scoped UDFs never reach the renderer — the field map is built only from the assessment's own definitions (`DocxReportGenerationService.java:386`). |
| §3.1.2 Scope Functions | Repeating rows, no loop. |
| §3.1.3 Provided Credentials | Repeating rows, no loop. |
| ~~§4.1 / §4.2 / §4.3 checklists~~ | **No longer a gap.** `${checklist-<name>}` is rendered in-process by `ChecklistTableRenderer`, matching a checklist by its title lowercased with hyphens. Labels and status colours are configured per template. Note the token takes no arguments. |

The common cause is one limitation: **Faction can only iterate over findings.** Six of the nine
were tables that repeat over something else; the two struck through above have since been closed
by rendering them in-process, which leaves seven.

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

**Superseded.** This originally recommended a `ReportManager` extension JAR as the cheapest
route. That is no longer available: the App Store and its extension runtime were removed from this
fork, because uploading a JAR that executes on the server is not a trade worth making. Checklists
are now rendered in-process instead, so §4.x works without either a JAR or the variables below.
The `${checklistBegin …}` design is kept here as the richer form — per-item iteration with the
template's own row styling — if the fixed table `ChecklistTableRenderer` produces is ever not
enough.

---

## G. Verifying a render

1. Upload `report-templates/templates/mobile/isec-mobile-upload.docx` as a report template.
2. Create the sections from §A.1 and all eight fields from §B, including the two rich-text
   defaults in §B.3.
3. Create an assessment with at least one finding in **each** of the three sections, and at
   least one section left empty, to exercise both the repeat and the `${if-section}` removal.
4. Generate, then check:
   - no literal `${…}` text anywhere in the output;
   - §2.10.x tables have one row per finding and no leftover config row;
   - the empty section's heading is gone entirely;
   - severity cells are coloured, not white (white means the `${cells}`/`${fill}` keys do not
     match your severity display names — see §A.2);
   - each finding starts on its own page;
   - §3.2 and §6.0 have a body, not just a heading — an empty one means the field exists but
     its default was never set (§B.3).
5. Open in Word, Ctrl+A, F9 to resolve the table of contents page numbers and refresh heading
   numbering.

### G.1 The table of contents

The template uses `${TOC}` rather than a Word TOC field. Faction replaces it at render time
with a generated table of contents and a page break after it (`DocxUtils.java:2036`).

This was a deliberate swap, with one loss. The original Word field was `TOC \o "1-6"`, covering
headings 1 through 6; `${TOC}` hardcodes `TOC \o "1-3"` with no way to configure the depth — it
is a string literal in the renderer, not an argument like the date variables take. So **the
`Proof Of Concept` sub-headings (Heading 4) no longer appear in the contents.** Verified by
running the generator over a document with headings at all four levels: three `PAGEREF` entries
and no `TOC4`-styled paragraph.

What it buys is that the contents are always correct for the report that was actually
generated. The Word field carried a cached snapshot from the original iSec document — it still
listed `5.1.1 Finding 1 (Un/Shielded)` and so on, findings that no longer exist — and anyone
who forgot to refresh shipped a contents page describing a different report. That failure mode
is now impossible.

Page numbers are still `PAGEREF` fields, as in any Word TOC, so they resolve when Word opens
the document or on F9. Step 5 above is what fills them in.
