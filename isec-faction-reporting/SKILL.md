---
name: isec-faction-reporting
description: Operate the iSec Faction penetration-testing reporting platform (a fork of OWASP Faction 2) through its REST API — create clients, targets, assessments/engagements and findings, attach checklists, bulk-schedule from CSV, manage report templates and generate DOCX/PDF reports. Use this whenever the user wants to do anything in Faction programmatically or by an agent: "create an engagement for client X", "add these vulnerabilities", "generate the report", "upload a template", "import this schedule", "list open findings", even if they only say "the reporting platform" or "the portal".
---

# iSec Faction — operating the platform through its API

Faction is a pentest reporting platform: findings are recorded against an engagement, and the
platform renders them into the client's Word/PDF report through a DOCX template. Everything the
web UI does is available over REST — **317 operations**.

| Resource | Use it for |
|---|---|
| This file | The domain model, the order things must happen in, and the traps. Read it first. |
| [`references/api/`](references/api/README.md) | Every operation, grouped by area, with its permission, its request fields and any rule the server enforces that the schema does not show. Look an endpoint up here before calling it. |
| [`references/openapi.json`](references/openapi.json) | The full OpenAPI 3.1 spec. If your platform imports OpenAPI as tools, import this. |
| [`scripts/faction.py`](scripts/faction.py) | A dependency-free command-line client. If you can run a shell, use it rather than hand-building HTTP requests (section 3). |

## 1. The domain model — learn this first

```
Client ──< Target ──< Assessment ──< Vulnerability (finding)
                         │
                         ├── assessment type     (Web, Network, Mobile, AD, Wireless, Red Team…)
                         ├── report template     (DOCX + CSS + user-defined fields)
                         └── checklists, retests, peer review, generated reports
```

**The UI and the API use different words for the same things.** Translate when you talk to a
human:

| The UI says | The API says | Endpoint root |
|---|---|---|
| Client | organization | `/api/v1/organizations` |
| Target | application | `/api/v1/applications` |
| Assessment / engagement | assessment | `/api/v1/assessments` |
| Finding | vulnerability | `/api/v1/assessments/{id}/vulnerabilities` |

**The chain is enforced.** A target must belong to a client (`organizationId` is required), and an
assessment must reference a target. The platform will not invent a target from an assessment's
name. So the order is always: client → target → assessment → findings → report.

## 2. Connecting

**Base URL:** wherever the backend runs, e.g. `http://localhost:8080`. All routes start `/api/v1`.
`GET /api/v1/status` needs no auth and is a good liveness check.

**Authentication — prefer an API key.** Send it as a bearer token:

```
Authorization: Bearer sk_fac_xxxxxxxxxxxxxxxx
```

API keys start `sk_fac_`, do not expire like a session, and carry a scope: `READ_WRITE` or
`READ_ONLY`. Give an agent a `READ_ONLY` key unless it genuinely needs to write. Create one with
`POST /api/v1/api-keys` `{"name": "...", "scope": "READ_WRITE"}`; the raw key is in
`data.key` and is shown **once** — store it immediately.

The alternative is a session JWT: `POST /api/v1/auth/login` `{"username","password"}`. Note the
token comes back at the **top level** (`response.token`), not inside `data` like everything else.
It expires after 24 hours.

**Make `GET /api/v1/auth/me` your first call.** It returns the effective authorities of whatever
token you hold, which is exactly what you are allowed to do — far better than discovering it one
403 at a time. Like login, it is **not** enveloped: the fields `username`, `id`, `roles`,
`authorities` sit at the top level. `super_admin` means everything; otherwise permissions read
`resource:action:scope`, e.g. `assessments:create:team`.

**Response shapes.** Success:

```json
{ "success": true, "message": "...", "data": { } , "pagination": null, "timestamp": "..." }
```

`data` is an object or a list. List endpoints take `page` (0-based) and `size`, and fill
`pagination`: `page, size, totalElements, totalPages, first, last`. Keep paging until `last` is
true — do not assume one page is everything.

Errors use a **different** shape: `{ "timestamp", "status", "error", "message", "path" }`. Read
`message`; it is usually specific ("Client is required", "Assessment not found with id: …").

| Status | Meaning here |
|---|---|
| 400 | Validation — read `message`, fix the body. |
| 401 | Missing or bad token / key. |
| 402 | The feature is switched off in this installation (e.g. external client accounts). Not a bug to work around. |
| 403 | The key's user lacks the permission for this action, or a `READ_ONLY` key tried to write. |
| 404 | Wrong id — or the record was soft-deleted. |
| 409 | The record's state forbids it — e.g. regenerating the report of a completed assessment. |

## 3. The command-line client

`scripts/faction.py` needs only Python 3. Configure it with environment variables —
`FACTION_URL`, and either `FACTION_TOKEN` (an API key or JWT) or `FACTION_USER` and
`FACTION_PASSWORD`. It prints JSON, reports failures as JSON on stderr with a non-zero exit, and
never prints the token.

```
python scripts/faction.py whoami                         # what this token may do
python scripts/faction.py find report generate           # search operations, offline
python scripts/faction.py call GET /assessments --all-pages
python scripts/faction.py call POST /organizations --data '{"name": "OneBank"}'
python scripts/faction.py call POST /assessments --data @assessment.json
python scripts/faction.py fields <templateId>            # UDF ids, for initialFieldValues
python scripts/faction.py upload /organizations/<id>/images --file logo.png --field name=logo
python scripts/faction.py generate <assessmentId> --out-dir ./reports
```

Paths may drop the `/api/v1` prefix. `--all-pages` follows pagination to the end and merges every
page into one `{success, data, count}` — that shape is the client's, not the API's; without the flag
you get the API's own envelope, `pagination` included. `generate` does the whole asynchronous
dance — trigger, poll, download — and then opens the DOCX and lists any `${…}` placeholders that
survived, which is the fastest way to find a field nobody filled in. `--types DOCX` fetches only the
Word file; `--name report` saves it as `report.docx`.

## 4. Core workflows

These were all executed end to end against a live installation. Request bodies show the fields
that matter; the spec has the rest.

### Reading state — open, closed, how many

Statuses are labels from a configurable workflow, so the same meaning is spelled differently across
installations and even across records (`New`, `IN_PROGRESS`, `Completed`, `Failed Retest`). Do not
string-match them. Use the fields that carry the meaning:

| Question | Read |
|---|---|
| Is an assessment open? | its boolean `completed` |
| Is a finding open? | its `closedAt` — null means open |
| How many findings of each severity? | the assessment's `vulnerabilitySummary`: `{critical, high, medium, low, informational}` — no need to list the findings |

**`showCompleted` includes completed assessments by default**, despite what its description says.
To list only open ones, pass `showCompleted=false` explicitly.

### Look things up before creating

```
GET /api/v1/organizations              clients
GET /api/v1/applications               targets (filter by organizationId)
GET /api/v1/assessment-types           Web Application Pentest, Network Assessment, …
GET /api/v1/report-templates           templates, and which assessment type each serves
GET /api/v1/vulnerability-categories   OWASP Top 10 categories, for findings
```

Reuse an existing client or target rather than creating a near-duplicate. Names are not unique
keys — match carefully and ask if two look alike.

### Create a client

`POST /api/v1/organizations`

```json
{ "name": "OneBank", "description": "...",
  "distributionList": [ { "name": "Mariam Haddad", "title": "CISO", "email": "m@onebank.example" } ] }
```

The distribution list feeds the report's contact table. Upload a logo with
`POST /api/v1/organizations/{id}/images` as multipart: `file` + `name=logo`. **Use PNG or JPEG** —
the report engine cannot rasterise SVG, so an SVG logo silently produces an empty box.

### Create a target

`POST /api/v1/applications`

```json
{ "name": "OneBank Internal Network", "organizationId": "<client id>",
  "appId": "ONEBANK-NET-001",
  "urls": [ { "url": "https://portal.onebank.example", "title": "Production portal" } ] }
```

`organizationId` is required. Each URL needs both `url` and `title`.

### Create an assessment, with its report fields

1. `GET /api/v1/report-templates/{templateId}` and read `data.userDefinedFields`. Each field has an
   `id`, a `variableName`, a `fieldType` and a `fieldScope` (`ASSESSMENT` or `VULNERABILITY`).
2. `POST /api/v1/assessments`:

```json
{ "name": "OneBank — Internal Network Penetration Test",
  "applicationId": "<target id>",
  "assessmentTypeId": "<type id>",
  "reportTemplateId": "<template id>",
  "startDate": "2026-09-14T09:00:00", "plannedEndDate": "2026-09-18T17:00:00",
  "scope": "10.0.0.0/16, AD forest corp.onebank.local",
  "initialFieldValues": { "<field id>": "1.0", "<field id>": "<p>Rich text is HTML.</p>" } }
```

**`initialFieldValues` is keyed by field `id`, not by `variableName`.** Sending
`{"report_version": "1.0"}` fails with "Unknown field ID". Build the map from step 1. RICH_TEXT
fields take HTML; DROPDOWN fields must use one of the field's `dropdownOptions`.

**Every required field needs a value — but the platform supplies defaults itself.** A field left
empty is printed with its `defaultValue` when the report renders, so you only have to supply the
required fields that have **no** default — including `VULNERABILITY`-scope ones, which every finding
then needs (an impact narrative, say). A required field with neither a value nor a default does not
fail the request; it prints as a literal `${…}` in the delivered report. Ask the user for those, or
write a sensible value and say you did. `faction.py fields <templateId>` lists every field with
`needsValue`, its `defaultValue` and its `dropdownOptions`, and puts the ones you must fill in
`needValue`.

Dates are local date-times with no timezone (`2026-10-06T09:00:00`). Given only a date, use 09:00
for a start and 17:00 for an end, and say so.

### Add findings

`POST /api/v1/assessments/{assessmentId}/vulnerabilities`

```json
{ "name": "Kerberoastable service account with a weak password",
  "severity": "HIGH",
  "cvssScore": 8.1, "cvssString": "CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N",
  "assetLocation": "svc_sql@corp.onebank.local",
  "vulnerabilityCategoryId": "<category id>",
  "description": "<p>…</p>", "details": "<p>Step 1 …</p>", "recommendation": "<p>…</p>",
  "order": 0,
  "fieldValues": { "<vulnerability-scope field id>": "…" } }
```

`severity` is one of `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFORMATIONAL` — the enum, even if the
installation displays different labels. `details` is where proof-of-concept steps go; write them
as numbered steps with the actual requests and responses. Key `fieldValues` by field id, like the
assessment's — the renderer also accepts `variableName` here, because the UI's finding editor
stores them that way, but the id is unambiguous.

### Generate and download the report

Generation is **asynchronous**:

1. `POST /api/v1/reports/{assessmentId}/generate` with body `{}` — returns at once.
2. Poll `GET /api/v1/reports/{assessmentId}/documents` every few seconds until no document has
   `status: "GENERATING"`. Each entry is `{type, status, errorMessage}` with `type` one of `DOCX`,
   `PDF`, `ENCRYPTED_PDF`. `data.reportPassword` is the encrypted PDF's password.
3. `GET /api/v1/reports/{assessmentId}/documents/{type}/content` returns the file bytes.

The DOCX is usually ready in seconds; the PDFs take longer because they go through LibreOffice —
allow a minute or more. A `FAILED` status carries the reason in `errorMessage`.

A **completed** assessment refuses regeneration with 409 until it is reopened. A completed
engagement has usually been delivered, so ask before reopening one.

**Check the output before calling it done.** Open the DOCX and search for `${` — any surviving
placeholder means a template field was not filled. The most common cause is a field left empty
on the assessment.

### Bulk-schedule assessments from CSV

1. `GET /api/v1/assessments/import/template` — the CSV header the installation expects, plus any
   custom-field columns. The built-in columns are `name, client, appId, applicationName,
   assessmentType, startDate, endDate, durationDays, assessors, campaign, team, engagementManager,
   remediationManager, reportTemplate, scope`.
2. `POST /api/v1/assessments/import/preview` (multipart `file`) — validates every row and writes
   nothing. Each row reports its resolved client, target, type and dates, flags targets it will
   create as new, and lists errors.
3. `POST /api/v1/assessments/import` — commits, all or nothing.

**Every row needs a `client` column** naming an existing client (matched case-insensitively). A
row may name a target that does not exist yet; the import creates it under that client. Always
preview first and show the human the rows marked new — an import is the fastest way to create
duplicates.

Dates are ISO `YYYY-MM-DD`. Give either `endDate` or `durationDays`. Several assessors go in one
cell separated by `;`, each a username or email. Types, teams, campaigns and templates are matched
by name, ignoring case; a campaign that does not exist is created if the caller may create them.

### Checklists

1. `POST /api/v1/checklist-templates` `{name, assessmentTypeId, questions: [{text, order}]}`.
2. `POST /api/v1/assessments/{id}/checklists` `{templateId}` attaches it.
3. `PUT /api/v1/assessments/{id}/checklists/{checklistId}`
   `{responses: [{questionId, questionText, result, comment, order}]}`, where `result` is `PASS`,
   `FAIL` or `NA`.

A report template prints a checklist with `${checklist-<name>}` — the checklist's title lowercased
with spaces turned into hyphens — and charts it with `${chartData checklist:<name>}` placed just
before a native Word chart. The table's look comes from the template's `checklistConfig`: labels,
colours, headers, `showComments`, `showDone` (the iSec "Done" column). Any key prefixed with a
checklist's name, such as `owasp-web-top-10.passText`, applies to that checklist's table alone.
`PUT /report-templates/{id}` with a whole `checklistConfig` replaces it, so send the existing keys too.

### Report templates

1. `POST /api/v1/report-templates` `{name, assessmentTypeId, css, font, scoringType: "CVSS_31",
   userDefinedFields: [...]}`.
2. `POST /api/v1/report-templates/{id}/file` — multipart `file`. The part **must** carry the type
   `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, or it fails with 400.
3. `PUT /api/v1/report-templates/{id}` updates fields, CSS, sections and render settings.

Never upload a template that contains Word comments: they are copied into every report generated
from it. How templates are built is documented in the repository under `report-templates/`
(`AGENT.md` for what each tag does, `CONVERSION_WORKFLOW.md` for the process).

## 5. Working sensibly

- **Read before you write.** Listing first is cheap; cleaning up a duplicate client or an orphan
  target is not.
- **Deletes are soft.** A deleted record disappears from lists and returns 404, but is kept in the
  database. Do not delete anything the user did not ask you to.
- **Confirm before bulk or destructive actions** — imports, deletes, regenerating a delivered
  report. State what will change and wait for a yes.
- **Report what you did with the ids**, so the human can find each record in the UI.
- **Never print secrets**: API keys, JWTs, the encrypted-PDF password unless asked.

## 6. Everything else

The API covers far more than this file walks through — retests, peer review, remediation queues,
the notebook, workflows, campaigns, users/teams/roles, notifications, email, AI prompt
configuration, dashboards and audit logs. [`references/api/README.md`](references/api/README.md)
indexes all of it by area; open the one file you need rather than all of them. `faction.py find`
searches the same ground from the command line.

The same conventions hold everywhere: bearer auth, the success/error envelopes, ids for every
relation, and the server's message telling you what was wrong.

**Keeping this current.** The reference is generated, so after the platform is upgraded,
regenerate it from the running installation rather than editing it by hand:

```
python scripts/build_reference.py --spec http://localhost:8080/v3/api-docs --save-spec \
       --source <checkout>/backend/src/main/java
```

`--source` is optional; it adds the permission each endpoint needs, read from the backend code.
