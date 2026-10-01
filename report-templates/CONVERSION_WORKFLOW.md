# Converting a Word report template for Faction

You hand over a `.docx` that knows nothing about Faction. You get back a template Faction fills
with real engagement data, plus the field definitions and the stylesheet that make it work.

**This file is the whole process**: the rules the work follows, what you provide, what comes back,
the phases and where I stop and ask, how the document is prepared, how a finished template is
validated, the scripts, and the environment it is tested in.

[`AGENT.md`](AGENT.md) is the companion *reference* — what each variable is, where it may be
placed, and how the renderer behaves. This file says how the work is run; that one says what the
engine will do. Where the two disagree about engine behaviour, `AGENT.md` wins.

Three templates have been through this: **Web** (WAPT, v9), **Network** (INT_EXTNWPT, v6) and
**Mobile** (MAPT). Their folders under `templates/` are the worked examples.

---

---

## 1. Standing rules

1. **The template is the product.** Fix layout in the template whenever possible. A change to
   the Faction source (`backend/src/main`, `frontend/src`) needs an explicit go from the
   developer, every time, with what / why / blast radius / alternative stated first
   (`CLAUDE.md` in the fork). Commits and pushes are separate permissions.
2. **Two files per template.** An annotated master (`isec-<type>-annotated.docx`, Word comments by
   "Faction Mapping" at every gap) and an upload copy (`isec-<type>-upload.docx`, no
   comments). Faction copies Word comments into every generated report, so never upload the
   master.
3. **Never fake a mapping.** If Faction has no variable for something, leave it static or make it
   a UDF and record the gap. Do not use a look-alike variable (`${impact}` is a rating, not the
   impact narrative; `${asmtAppid}` is an ID, not the application name).
4. **Every change is verified in both renderers**: the DOCX opened in Word, and the PDF that
   Faction produces (LibreOffice in the backend container). They disagree in specific ways
   (`AGENT.md` section 12). Something that looks right in Word is not done.
5. **Keep the assessment-specific content.** Only the integration mechanics are copied from the
   Web template; methodology, checklists, section wording and the findings layout belong to the
   assessment type (`AGENT.md` section 15).
6. **Version the template** (v1, v2 …), keep the previous upload copy in `samples/`, and write
   what changed into the mapping document's history list. Scripts that transform a template go
   into `tools/template-edits/` with the version they produce.

---
---

## 2. What you give me

**Required**

| | |
|---|---|
| The document | The original `.docx`, exactly as your team uses it today. Not a cleaned-up copy — I need to see the real tables, text boxes, numbering and headers. |
| The engagement type | Web, Network, Mobile, Active Directory, Wireless, Red Team… This decides the assessment type it binds to and what the findings section has to carry. |

**Helpful, not required**

- A filled-in example of the same report from a past engagement, so I can see what each blank
  normally holds. This is the single most useful extra thing you can provide.
- Your house CSS, if you have one. Otherwise it starts from `shared/isec-report.css`.
- Whether the client logo should appear, and where.

**What I do not need:** you don't need to mark anything up, remove anything, or guess which parts
Faction can fill. Working that out is the job.

---

---

## 3. What comes back

```
templates/<type>/
├── README.md                      what each file is, what it needs from the backend
├── MAPPING.md                     section-by-section mapping, gaps, version history
├── isec-<type>-udfs.json          the user-defined fields, ready to PUT
├── isec-<type>-original.docx      your file, untouched, for reference
├── isec-<type>-upload.docx        THE UPLOAD FILE — no Word comments
├── isec-<type>-annotated.docx     the same template + a comment at every decision
└── samples/
    └── v<n>-report.docx / .pdf    a generated report at that version
```

**Two files per template, and only one of them is ever uploaded.** Word comments survive into
every generated report, so the annotated master is documentation and must never reach Faction.
The upload copy is comment-free and verified so before it ships.

The version lives in the sample filenames and in `templates/<type>/MAPPING.md`'s history, not in the template's own
name: git already versions the template, and renaming it every revision would break `--follow` and
leave stale files behind.

---

---

## 4. The phases

### Phase 1 — Read the document (no changes yet)

I inventory every section, placeholder, repeated structure, image, text box, header, footer, table
style and numbering definition, and render the original to PDF as the reference picture. Then
every field in the document is sorted into exactly one of four buckets:

| Bucket | Meaning |
|---|---|
| **Static** | Boilerplate. Stays as typed. |
| **Built-in** | Faction already has a variable — client name, dates, assessors, findings, CVSS, severity counts. |
| **UDF** | Faction has no variable, but the data is per engagement, so it becomes a user-defined field you fill in on the assessment. |
| **Gap** | Faction cannot represent it at all. Recorded, not faked. |

**→ Checkpoint 1.** You get the mapping before I touch the document. This is the cheapest moment
to correct me, and the one where your knowledge of what the fields actually mean matters most.

### Phase 2 — Set Faction up

The assessment type, the user-defined fields, the stylesheet and the report template record. The
UDF set is deliberately shared across templates where the meaning is the same (`limitations`,
`recommendations`, `project_name`), so an assessor filling in a Network report meets the same field
names they already know from a Web one.

**→ Checkpoint 2** if new UDFs are needed: you approve the names, types and defaults before they
exist, because renaming one later means editing every template that references it.

### Phase 3 — Convert the document

Section 5 is the detailed procedure. Scripted, not hand-edited, so it is repeatable and reviewable.

### Phase 4 — Generate and measure

Section 6 is the checklist. A throwaway client, target and assessment with deliberately awkward
data: long values, short values, findings across every severity, rich text with tables and
screenshots, a finding with several assets.

### Phase 5 — Hand over

Upload copy, annotated master, mapping document, UDF JSON, a generated sample, and the version
history recording what changed and how it was verified.

**→ Checkpoint 3.** You read a real generated report before it is used on a real engagement.

---

---

## 5. Preparing the Word document

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
5. **Restyle every `TableGridLight` table to `TableGrid` and give every cell its own light
   border** (single, 0.5 pt, `BFBFBF`), completing the undeclared edges of partially bordered
   merged cells. Reason: the LibreOffice round trip writes an empty `<w:tcBorders/>` on every
   cell of a table whose style id is `TableGridLight`, whatever the cells declare, and drops
   table-level `tblBorders`; Word then draws no grid at all. Under `TableGrid` the per-cell
   borders survive with their colour, so the report keeps the light grey line of the iSec
   originals. `tools/template-edits/table_fix.py` does this for a whole template (it marks
   touched tables with `data-was-light`); `build_network_v6.py` is the same cure applied after
   v5 had wrongly gone back to the style name. Cells copied in from the original template carry
   no borders of their own and fall back to the style's black: give them borders too. The MAPT
   template got the same cure (fork commit `643ec3e`).
6. **Fix cover text boxes that clip**: the date box in the Web template clipped "September 18,"
   because its DrawingML extent was too narrow. Widen the box (`wp:extent` and the VML fallback
   `style` width), left-align the paragraph, and set the insets so text clears the decorative
   shapes. See `cover_fix.py` for the exact edits.
7. **Set the template's Report Font** in the Report Designer to the family the template uses in
   Word (Calibri for iSec). `AGENT.md` section 11 explains why.
8. **Upload the CSS** (`shared/isec-report.css`) in the Report Designer, then the UDFs
   (`AGENT.md` section 3), then the DOCX.

What stays unchanged: page size and margins, the section breaks, the header/footer structure,
the Word numbering definitions (they number the findings), the styles, all static text.

**Work on the XML, not only in Word.** Every structural change we made (text box geometry,
borders, highlights, footer boxes) was scripted against the package XML with Python's
`zipfile` + `re`/`lxml`, so it is repeatable and version-controlled. Word's UI is used to
inspect and to type tags, scripts are used to transform. After a script, always open the result
in Word once (a broken XML shows "unreadable content").

---
---

## 6. Validation

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
`tools/verify/verify_network_v3.py`, `verify_network_v5.py` and `verify_network_v6.py` (charts,
CVSS links, scope tables and, from v6, the cell borders read back out of a generated report),
`tools/verify/kali_lo_roundtrip.sh` (run a template through the backend container's LibreOffice
before generating anything: the DOCX it returns is what Faction hands out),
`tools/faction/minio_pull.sh` (copy the generated files straight out of the MinIO volume when the
API session is not available).

The reverse also holds: a correct PDF does not prove the DOCX. The PDF is drawn from LibreOffice's
own model, the DOCX is what LibreOffice writes back out, and the two disagree on table borders
(lesson 7). Open the generated DOCX and check `tcBorders` on its cells, or run
`verify_network_v6.py`, before calling table lines fixed.

---
---

## 7. The repeatable sequence

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
7. Build the summary table and the findings block exactly as in `AGENT.md` sections 8 and 9, then adapt the
   columns and rows to the assessment type.
8. Produce the two files (annotated master with "Faction Mapping" comments at every gap, comment-free
   upload copy). Open both in Word once.
9. Run the engine offline if a fork checkout is available (a throwaway JUnit test that loads the
   DOCX, as done in this project) or upload to a throwaway template in the local Faction
   ("<Name> (test)") and generate on a throwaway assessment.
10. Validate per section 6; fix; repeat. Keep each version's upload file in `samples/`.
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
---

## 8. Tools

**These scripts are not in this repository.** They live on the conversion workstation under a
`tools/` directory and carry hard-coded paths from the session that produced them (scratchpads,
the Web template, OneBank ids). The table is here so the work is reproducible and so the next
conversion knows what already exists rather than rebuilding it; copy a script and edit the
constants at the top before use.

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
| `template-edits/build_network_v5.py` | v4→v5: the 3.1.1 table pinned to fixed twips; also put the 12 light tables back on the `TableGridLight` style, which v6 had to undo. |
| `template-edits/build_network_v6.py` | v5→v6: the 12 tables back on `TableGrid` with a `BFBFBF` border on every cell, the only form LibreOffice returns intact in the DOCX (lesson 7). |
| `verify/build_placement_test.py` | Builds a probe template with the tag in every kind of place (cover box, cell, right-aligned, mixed, unknown slot, header) to learn what the engine honours. |
| `verify/inspect_placement.py` | Reports where images landed in a generated DOCX (boxes, cells, body, header) with sizes. |
| `verify/render_where.py` | Renders template and generated pages (Word COM + PyMuPDF), boxes the tag/image, side-by-side sheet. |
| `verify/verify_v9.py` | Word and container-LibreOffice renders of an engine output, logo and page-number measurements, band crop, comparison sheet. |
| `verify/verify_network_v3.py`, `verify_network_v5.py`, `verify_network_v6.py` | Read a generated Network report back and assert it (v6 adds: no cell with an empty `tcBorders`, light borders on the cells, no `TableGridLight` left): both charts against the engagement's real data (cached values and embedded workbook), one NVD link per finding carrying that finding's vector, the scope and credentials cells, no surviving tag or marker. |
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
| `faction/kali_minio_pull_network.sh` | Copies an assessment's newest generated DOCX and PDF out of MinIO on the VM through the S3 API (`mc` inside the minio container), byte for byte what Faction serves; a raw copy of `part.1` is not (lesson 27). |
| `verify/kali_lo_roundtrip.sh` | Runs a DOCX through the backend container's LibreOffice (docx and pdf out), the same converter Faction uses, so a template can be proven before a report is generated. |
| `faction/sync_to_kali.sh` | Copies the given project-relative paths from the Windows backup copy to the live Kali working directory and prints their checksums. |
| `faction/Set-FactionKaliPortProxy.ps1` | Windows, elevated: repoints the `127.0.0.1:8080` portproxy rule at the VM's current IP (read from `vmrun`) and verifies `http://localhost:8080`. |
| `faction/network_checklist_questions.json` | The 13 iSec Network checklist rows taken from the template's own 4.1 table, in the order the checklist template wants them. |

Requirements on the workstation: Python 3 with `pywin32` (Word COM export), `PyMuPDF`, `Pillow`,
`numpy`, `lxml`; Microsoft Word; the Kali VM with Docker for the local Faction (section 9); `curl`.
The `kali_*` scripts run on the VM over `ssh kali`, with the project at `/home/kali/Reporting System/`.

---
---

## 9. The local Faction, for testing a conversion

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
---

## 10. Where this usually goes wrong

Honest expectations, from the three conversions so far:

- **It takes several versions.** The Web template reached v9, Network v6. Each version fixed
  something only a rendered report could reveal. Budget for iteration, not a single pass.
- **The first generated report will look wrong somewhere.** That is the process working — it is
  far cheaper to find it here than on a client deliverable.
- **Some things genuinely cannot be mapped**, and you will get a list rather than a workaround.
- **A template referencing a feature the backend lacks will fail quietly.** Client images in
  headers and footers needed an engine change before they worked at all.

---

---

## 11. What I need decided, not guessed

I will stop and ask about these rather than pick for you:

1. **Anything ambiguous in the mapping** — when a heading could plausibly be two different
   variables, your answer decides it.
2. **New UDF names and defaults**, because they are shared across templates.
3. **Scope cuts** — if a section would need engine work, you choose: static text, a UDF, or a
   change request against the backend.
4. **Anything that would change the application's source code.** Per `CLAUDE.md` that needs your
   explicit go-ahead every time, with what / why / blast radius / alternative stated first. The
   template is the product: layout gets fixed in the document wherever it possibly can be.

---

---

## 12. Quick reference

| You want | Say |
|---|---|
| Start a conversion | "Convert this template for Faction" + the `.docx` + the engagement type |
| A new version of an existing one | "Template X needs …" — it goes to v(n+1) with history |
| Just the mapping, no conversion | "Map this template, don't convert it" — stops after Phase 1 |
| Know what Faction can't do | Ask for the gaps list in `BACKEND_GAPS.md` and the template's `templates/<type>/MAPPING.md` |

**Companion files:** [`AGENT.md`](AGENT.md) is the engine reference — variables, placement rules
and the lessons behind them. [`BACKEND_GAPS.md`](BACKEND_GAPS.md) is what Faction cannot do yet.
`templates/web/` and `templates/network/` are the worked examples; read one alongside its template
before starting a new conversion.
