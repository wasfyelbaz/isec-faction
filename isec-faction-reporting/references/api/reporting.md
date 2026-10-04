# Report generation, templates and the content libraries they draw on

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Reports](#reports), [Report Templates](#report-templates), [Report Fonts](#report-fonts), [Default Vulnerabilities](#default-vulnerabilities), [Vulnerability Categories](#vulnerability-categories), [Content Templates](#content-templates), [Checklist Templates](#checklist-templates), [Survey Templates](#survey-templates), [Terminology](#terminology), [Entity Field Configs](#entity-field-configs)

## Reports

### `GET /api/v1/reports/{assessmentId}/documents`

**Get per-document generation status for an assessment's report.**
Returns the status, last-generated timestamp, and availability of each report artifact (DOCX, PDF, encrypted PDF), plus the password for the encrypted PDF once provisioned.

- **Permission:** `reporting:download`, `reporting:download:owned`, `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`
- **Returns:** `data` = `ReportDocumentsDto`

### `GET /api/v1/reports/{assessmentId}/documents/{type}/content`

**Download a generated report.**
Streams the generated report's bytes as an attachment download.

- **Permission:** `reporting:download`, `reporting:download:owned`, `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`, `type`

### `POST /api/v1/reports/{assessmentId}/generate`

**Trigger background report generation for an assessment.**
Starts an async job that generates the report from the assessment's DOCX template, stores it in MinIO, and updates the assessment with the download key. Returns 202 immediately.

- **Permission:** `reporting:create`, `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`
- **Rule:** Asynchronous: returns at once. Poll `GET /reports/{assessmentId}/documents` until no document is `GENERATING`, then download each with `.../documents/{type}/content`. Refused with 409 on a completed assessment — it must be reopened first; do not reopen a delivered engagement without asking. Prefer this over the older synchronous `POST /assessments/{id}/report/generate`, which produces fewer documents.

### `GET /api/v1/reports/{assessmentId}/pdf`

**Convert the generated DOCX report to PDF and return it inline.**
Downloads the stored DOCX report, converts it to PDF via LibreOffice headless, and streams the result. Suitable for in-browser preview.

- **Permission:** `reporting:download`, `reporting:download:owned`, `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`

### `POST /api/v1/reports/{assessmentId}/upload`

**Upload a DOCX or PDF report, replacing the generated artifacts.**
Uploading a DOCX replaces the DOCX artifact and regenerates both the PDF and encrypted PDF from it. Uploading a PDF replaces only the plain PDF and regenerates the encrypted PDF from it, leaving the DOCX untouched. Returns 202 immediately; poll GET /{assessmentId}/documents for progress.

- **Permission:** `reporting:create`, `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`
- **Body:** multipart/form-data

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

## Report Templates

### `GET /api/v1/report-templates`

**Search report templates.**
Search report templates with pagination and filters

- **Permission:** `report_templates:read:all`
- **Query:** `page`, `size`, `name`, `assessmentTypeId`, `active`, `sort`

### `POST /api/v1/report-templates`

**Create report template.**
Create a new report template with user-defined fields

- **Permission:** `report_templates:create:all`
- **Body:** JSON — `CreateReportTemplateRequest`
- **Returns:** `data` = `ReportTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |
| `assessmentTypeId` | `string` | yes |  |
| `css` | `string` |  |  |
| `font` | `string` |  |  |
| `scoringType` | `string` |  |  |
| `checklistConfig` | `map<string, string>` |  |  |
| `barChartConfig` | `map<string, string>` |  |  |
| `sections` | `string[]` |  |  |
| `userDefinedFields` | `UserDefinedFieldDto[]` |  |  |

### `GET /api/v1/report-templates/by-assessment-type/{assessmentTypeId}`

**Get templates by assessment type.**
Retrieve all active templates for a specific assessment type

- **Permission:** `report_templates:read:all`
- **Path:** `assessmentTypeId`
- **Returns:** `data` = `ReportTemplateSummaryDto`

### `GET /api/v1/report-templates/vulnerability-fields`

**Get vulnerability-scoped fields.**
Retrieve all VULNERABILITY-scoped user-defined fields across all active report templates, deduplicated by variable name

- **Permission:** any signed-in user
- **Returns:** `data` = `ListUserDefinedFieldDto`

### `DELETE /api/v1/report-templates/{id}`

**Delete report template.**
Delete a report template. Soft delete if assessments exist, hard delete otherwise.

- **Permission:** `report_templates:delete:all`
- **Path:** `id`
- **Returns:** `data` = `MapStringString`

### `GET /api/v1/report-templates/{id}`

**Get report template by ID.**
Retrieve a single report template with full details

- **Permission:** `report_templates:read:all`
- **Path:** `id`
- **Returns:** `data` = `ReportTemplateDto`

### `PUT /api/v1/report-templates/{id}`

**Update report template.**
Update an existing report template. Version increments if fields are modified.

- **Permission:** `report_templates:edit:all`
- **Path:** `id`
- **Body:** JSON — `UpdateReportTemplateRequest`
- **Returns:** `data` = `ReportTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |
| `assessmentTypeId` | `string` |  |  |
| `css` | `string` |  |  |
| `font` | `string` |  |  |
| `checklistConfig` | `map<string, string>` |  |  |
| `barChartConfig` | `map<string, string>` |  |  |
| `reportPalette` | `ReportPalette` |  |  |
| `sections` | `string[]` |  |  |
| `userDefinedFields` | `UserDefinedFieldDto[]` |  |  |
| `active` | `boolean` |  |  |
| `scoringType` | `string` |  |  |

### `POST /api/v1/report-templates/{id}/clone`

**Clone report template.**
Duplicate an existing template under a new name. Everything that defines the template is copied exactly — description, assessment type, CSS, font, scoring type, sections, every user-defined field (ids and variable names included, so the DOCX's ${...} references still resolve) and the uploaded DOCX itself, copied to its own storage key. The clone starts at version 1 and is active.

- **Permission:** `report_templates:create:all`
- **Path:** `id`
- **Body:** JSON — `CloneReportTemplateRequest`
- **Returns:** `data` = `ReportTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes | Name for the new template; must not match an existing template |

### `GET /api/v1/report-templates/{id}/file`

**Download template file.**
Download the DOCX template file from S3/MinIO

- **Permission:** `report_templates:read:all`
- **Path:** `id`

### `POST /api/v1/report-templates/{id}/file`

**Upload template file.**
Upload a DOCX template file to S3/MinIO. Maximum file size: 50MB

- **Permission:** `report_templates:edit:all`
- **Path:** `id`
- **Body:** multipart/form-data
- **Returns:** `data` = `ReportTemplateDto`
- **Rule:** Send as multipart/form-data. The `file` part must carry the content type `application/vnd.openxmlformats-officedocument.wordprocessingml.document` or the upload fails with 400. Never upload a template containing Word comments — they are copied into every report generated from it.

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

## Report Fonts

### `GET /api/v1/report-fonts`

**List the uploaded report fonts.**
One entry per uploaded file, with the family and style the font declares.

- **Permission:** `report_templates:read:all`
- **Returns:** `data` = `ListReportFontDto`

### `POST /api/v1/report-fonts`

**Upload and install one or more font files.**
TrueType or OpenType files (.ttf, .otf), up to 25 MB each. The family and style are read from each file; uploading a style that already exists replaces it. The fonts are installed at once and the report engine is restarted so they apply to the next report.

- **Permission:** `report_templates:edit:all`
- **Body:** multipart/form-data
- **Returns:** `data` = `ListReportFontDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `files` | `file[]` | yes |  |

### `GET /api/v1/report-fonts/installed`

**List every font family the server can render PDFs in.**
Bundled and uploaded fonts alike, as fontconfig reports them. A template's Report Font that is not in this list is substituted in the PDF.

- **Permission:** `report_templates:read:all`
- **Returns:** `data` = `ListString`

### `DELETE /api/v1/report-fonts/{id}`

**Delete an uploaded font.**
Removes the stored file and uninstalls it from the server.

- **Permission:** `report_templates:edit:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

## Default Vulnerabilities

### `GET /api/v1/default-vulnerabilities`

**Get all default vulnerabilities.**
Retrieve default vulnerability templates with pagination. Set archived=true to list archived entries instead of active ones.

- **Permission:** any signed-in user
- **Query:** `page`, `size`, `sort`, `archived`
- **Returns:** `data` = `ListDefaultVulnerabilityDto`

### `POST /api/v1/default-vulnerabilities`

**Create a default vulnerability.**
Create a new default vulnerability template.

- **Permission:** `default-vulnerabilities:create`
- **Body:** JSON — `CreateDefaultVulnerabilityRequest`
- **Returns:** `data` = `DefaultVulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `severity` | `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL` | yes |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |
| `cvssScore31` | `number(double)` |  |  |
| `cvssString31` | `string` |  |  |
| `cvssScore40` | `number(double)` |  |  |
| `cvssString40` | `string` |  |  |
| `description` | `string` |  |  |
| `recommendation` | `string` |  |  |
| `vulnerabilityCategoryId` | `string` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `order` | `integer(int32)` |  |  |

### `POST /api/v1/default-vulnerabilities/import`

**Import built-in default vulnerabilities.**
Imports the built-in library of default vulnerability templates, skipping entries that already exist.

- **Permission:** `default-vulnerabilities:create`
- **Returns:** `data` = `DefaultVulnerabilityImportResultDto`

### `DELETE /api/v1/default-vulnerabilities/{id}`

**Delete a default vulnerability.**
Permanently delete a default vulnerability template.

- **Permission:** `default-vulnerabilities:delete`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/default-vulnerabilities/{id}`

**Get a default vulnerability.**
Retrieve a single default vulnerability template by ID.

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `DefaultVulnerabilityDto`

### `PATCH /api/v1/default-vulnerabilities/{id}`

**Update a default vulnerability.**
Partially update an existing default vulnerability template.

- **Permission:** `default-vulnerabilities:edit`
- **Path:** `id`
- **Body:** JSON — `UpdateDefaultVulnerabilityRequest`
- **Returns:** `data` = `DefaultVulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `severity` | `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL` |  |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |
| `cvssScore31` | `number(double)` |  |  |
| `cvssString31` | `string` |  |  |
| `cvssScore40` | `number(double)` |  |  |
| `cvssString40` | `string` |  |  |
| `description` | `string` |  |  |
| `recommendation` | `string` |  |  |
| `vulnerabilityCategoryId` | `string` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `order` | `integer(int32)` |  |  |

### `PATCH /api/v1/default-vulnerabilities/{id}/archive`

**Archive a default vulnerability.**
Archive a default vulnerability template so it no longer appears in the active list.

- **Permission:** `default-vulnerabilities:edit`
- **Path:** `id`
- **Returns:** `data` = `DefaultVulnerabilityDto`

### `PATCH /api/v1/default-vulnerabilities/{id}/unarchive`

**Unarchive a default vulnerability.**
Restore an archived default vulnerability template to the active list.

- **Permission:** `default-vulnerabilities:edit`
- **Path:** `id`
- **Returns:** `data` = `DefaultVulnerabilityDto`

## Vulnerability Categories

### `GET /api/v1/vulnerability-categories`

**List all vulnerability categories.**

- **Permission:** any signed-in user
- **Returns:** `data` = `ListVulnerabilityCategoryDto`

### `POST /api/v1/vulnerability-categories`

**Create a vulnerability category.**

- **Permission:** `vulnerability-category:create`
- **Body:** JSON — `CreateVulnerabilityCategoryRequest`
- **Returns:** `data` = `VulnerabilityCategoryDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |

### `DELETE /api/v1/vulnerability-categories/{id}`

**Delete a vulnerability category.**

- **Permission:** `vulnerability-category:delete`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/vulnerability-categories/{id}`

**Get a vulnerability category by ID.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `VulnerabilityCategoryDto`

### `PATCH /api/v1/vulnerability-categories/{id}`

**Update a vulnerability category.**

- **Permission:** `vulnerability-category:edit`
- **Path:** `id`
- **Body:** JSON — `UpdateVulnerabilityCategoryRequest`
- **Returns:** `data` = `VulnerabilityCategoryDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |

## Content Templates

### `GET /api/v1/admin/content-templates`

**List content templates.**
Every template, enabled or not — the management view. Editors see only the enabled ones for their scope, via GET /api/v1/content-templates.

- **Permission:** `content-templates:create`, `content-templates:edit`, `content-templates:delete` (any of)
- **Returns:** `data` = `ListContentTemplateDto`

### `POST /api/v1/admin/content-templates`

**Create a content template.**
Adds a template to the picker of every editor in the scope it declares.

- **Permission:** `content-templates:create`
- **Body:** JSON — `SaveContentTemplateRequest`
- **Returns:** `data` = `ContentTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |
| `scope` | `ASSESSMENT|VULNERABILITY` |  |  |
| `content` | `string` |  |  |
| `enabled` | `boolean` |  |  |

### `DELETE /api/v1/admin/content-templates/{id}`

**Delete a content template.**
Removes the template. Editors stop offering it immediately; text already inserted from it is untouched.

- **Permission:** `content-templates:delete`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `PUT /api/v1/admin/content-templates/{id}`

**Update a content template.**
Replaces the template's title, description, scope, body, and enabled flag.

- **Permission:** `content-templates:edit`
- **Path:** `id`
- **Body:** JSON — `SaveContentTemplateRequest`
- **Returns:** `data` = `ContentTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |
| `scope` | `ASSESSMENT|VULNERABILITY` |  |  |
| `content` | `string` |  |  |
| `enabled` | `boolean` |  |  |

### `GET /api/v1/content-templates`

**List available content templates.**
The enabled templates for the given editor scope, name-ordered, with their bodies so the picker can preview and insert without a second request. Disabled templates are never returned.

- **Permission:** any signed-in user
- **Query:** `scope` (required)
- **Returns:** `data` = `ListContentTemplateDto`

## Checklist Templates

### `GET /api/v1/checklist-templates`

**Get all checklist templates.**

- **Permission:** any signed-in user
- **Query:** `assessmentTypeId`
- **Returns:** `data` = `ListChecklistTemplateDto`

### `POST /api/v1/checklist-templates`

**Create checklist template.**

- **Permission:** `checklist:create`
- **Body:** JSON — `CreateChecklistTemplateRequest`
- **Returns:** `data` = `ChecklistTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `assessmentTypeId` | `string` | yes |  |
| `questions` | `ChecklistTemplateQuestionDto[]` |  |  |
| `preventClosure` | `boolean` |  |  |

### `DELETE /api/v1/checklist-templates/{id}`

**Delete checklist template.**

- **Permission:** `checklist:delete`
- **Path:** `id`

### `GET /api/v1/checklist-templates/{id}`

**Get checklist template by ID.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `ChecklistTemplateDto`

### `PUT /api/v1/checklist-templates/{id}`

**Update checklist template.**

- **Permission:** `checklist:edit`
- **Path:** `id`
- **Body:** JSON — `UpdateChecklistTemplateRequest`
- **Returns:** `data` = `ChecklistTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `assessmentTypeId` | `string` |  |  |
| `questions` | `ChecklistTemplateQuestionDto[]` |  |  |
| `active` | `boolean` |  |  |
| `preventClosure` | `boolean` |  |  |

## Survey Templates

### `GET /api/v1/survey-templates`

**Get all survey templates.**

- **Permission:** any signed-in user
- **Query:** `active`
- **Returns:** `data` = `ListSurveyTemplateDto`

### `POST /api/v1/survey-templates`

**Create survey template.**

- **Permission:** `survey:create`
- **Body:** JSON — `CreateSurveyTemplateRequest`
- **Returns:** `data` = `SurveyTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `questions` | `SurveyTemplateQuestionDto[]` |  |  |

### `DELETE /api/v1/survey-templates/{id}`

**Delete survey template.**

- **Permission:** `survey:delete`
- **Path:** `id`

### `GET /api/v1/survey-templates/{id}`

**Get survey template by ID.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `SurveyTemplateDto`

### `PUT /api/v1/survey-templates/{id}`

**Update survey template.**

- **Permission:** `survey:edit`
- **Path:** `id`
- **Body:** JSON — `UpdateSurveyTemplateRequest`
- **Returns:** `data` = `SurveyTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `questions` | `SurveyTemplateQuestionDto[]` |  |  |
| `active` | `boolean` |  |  |

## Terminology

### `GET /api/v1/config/terminology`

**Get the configured terminology.**
Readable by any signed-in user: these labels appear on nearly every screen, so the interface cannot render correctly without them.

- **Permission:** any signed-in user
- **Returns:** `data` = `TerminologyConfig`

### `PUT /api/v1/config/terminology`

**Update the terminology.**
Renames organizations, sub-organizations and severities throughout the interface. Only the wording changes: findings keep the severity they were recorded at, and every filter, report token and export still uses CRITICAL..INFORMATIONAL. A field that is omitted or empty keeps the label it already had.

- **Permission:** any signed-in user
- **Body:** JSON — `TerminologyConfigRequest`
- **Returns:** `data` = `TerminologyConfig`

| Field | Type | Required | Notes |
|---|---|---|---|
| `organizationSingular` | `string` |  |  |
| `organizationPlural` | `string` |  |  |
| `targetSingular` | `string` |  |  |
| `targetPlural` | `string` |  |  |
| `subOrganizationSingular` | `string` |  |  |
| `subOrganizationPlural` | `string` |  |  |
| `severityCritical` | `string` |  |  |
| `severityHigh` | `string` |  |  |
| `severityMedium` | `string` |  |  |
| `severityLow` | `string` |  |  |
| `severityInformational` | `string` |  |  |

## Entity Field Configs

### `GET /api/v1/entity-fields/{scope}`

**Get field definitions for a scope.**
Retrieve the custom field definitions for APPLICATION or ORGANIZATION scope.

- **Permission:** `applications:read:all`, `applications:read:owned`, `organizations:read:all`, `organizations:read:owned` (any of)
- **Path:** `scope`
- **Returns:** `data` = `EntityFieldConfigDto`

### `PUT /api/v1/entity-fields/{scope}`

**Update field definitions for a scope.**
Update the custom field definitions for APPLICATION or ORGANIZATION scope. Only accessible to Super Admins.

- **Permission:** any signed-in user
- **Path:** `scope`
- **Body:** JSON — `UpdateEntityFieldConfigRequest`
- **Returns:** `data` = `EntityFieldConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fieldDefinitions` | `UserDefinedFieldDto[]` |  |  |
