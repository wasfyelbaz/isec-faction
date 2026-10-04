# Assessments, findings and the work around them

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Assessments](#assessments), [Vulnerabilities](#vulnerabilities), [Vulnerabilities (Global)](#vulnerabilities-global), [Retests](#retests), [Peer Reviews](#peer-reviews), [Remediation](#remediation), [Notebook](#notebook), [Assessment Checklists](#assessment-checklists), [Assessment Surveys](#assessment-surveys), [Inline Images](#inline-images), [Assessment Workflow Config](#assessment-workflow-config)

## Assessments

### `GET /api/v1/assessments`

**Search assessments.**
Search assessments with pagination and advanced filters

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:read:org`, `assessments:read:owned` (any of)
- **Query:** `page`, `size`, `search`, `applicationId`, `organizationId`, `assessmentTypeId`, `assessmentTypeIds`, `assessorId`, `status`, `statuses`, `openSurveys`, `startDateFrom`, `startDateTo`, `endDateFrom`, `endDateTo`, `completedDateFrom`, `completedDateTo`, `pastDue`, `showCompleted`, `onlyCompleted`, `assignedToMe`, `sort`
- **Rule:** `showCompleted` includes completed assessments by default, whatever its description says; pass `showCompleted=false` for open ones only. Tell open from closed by the boolean `completed`, not the workflow-defined `status` label. Each assessment carries `vulnerabilitySummary` with per-severity counts.

### `POST /api/v1/assessments`

**Create assessment.**
Create a new assessment from a report template. Snapshots the template's field definitions.

- **Permission:** `assessments:create:all`, `assessments:create:team` (any of)
- **Body:** JSON — `CreateAssessmentRequest`
- **Returns:** `data` = `AssessmentDto`
- **Rule:** A target is required even though the schema does not mark it: send `applicationId` of an existing target. `initialFieldValues` is keyed by the report template's field **id** (from `GET /report-templates/{id}` → `userDefinedFields[].id`), not by `variableName`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `applicationId` | `string` |  |  |
| `organizationId` | `string` |  |  |
| `appId` | `string` |  |  |
| `applicationName` | `string` |  |  |
| `campaignId` | `string` |  |  |
| `assessmentTypeId` | `string` | yes |  |
| `reportTemplateId` | `string` |  |  |
| `teamId` | `string` |  |  |
| `assessorId` | `string` |  |  |
| `assessorIds` | `string[]` |  |  |
| `engagementManagerId` | `string` |  |  |
| `remediationManagerId` | `string` |  |  |
| `startDate` | `string(date-time)` |  |  |
| `plannedEndDate` | `string(date-time)` |  |  |
| `scope` | `string` |  |  |
| `engagementUrls` | `EngagementUrlDto[]` |  |  |
| `stakeholders` | `StakeholderDto[]` |  |  |
| `initialFieldValues` | `map<string, string>` |  |  |

### `POST /api/v1/assessments/assessor-availability`

**Check which candidate assessors are free.**
Given a proposed window and a set of candidate assessors, reports which of them are already booked on an overlapping assessment, and on what. Answers for every candidate rather than only the ones already chosen, so a scheduler can see who is available before assigning.

- **Permission:** `assessments:create:all`, `assessments:create:team`, `vulnerabilities:create:all`, `vulnerabilities:create:team`, `vulnerabilities:create:assessment` (any of)
- **Body:** JSON — `AssessorAvailabilityRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `assessmentId` | `string` |  |  |
| `assessorIds` | `string[]` |  |  |
| `startDate` | `string(date-time)` |  |  |
| `endDate` | `string(date-time)` |  |  |

### `GET /api/v1/assessments/by-application/{applicationId}`

**Get assessments by application.**
Retrieve all assessments for a specific application

- **Permission:** `assessments:read:all`, `assessments:read:org`, `assessments:read:owned` (any of)
- **Path:** `applicationId`
- **Query:** `page`, `size`

### `GET /api/v1/assessments/calendar`

**Get assessments for calendar view.**
Get assessments within a date range for calendar visualization

- **Permission:** `assessments:read:all`, `assessments:read:org` (any of)
- **Query:** `startDate` (required), `endDate` (required), `page`, `size`

### `POST /api/v1/assessments/check-conflicts`

**Check for conflicting assessments.**
Check if there are any assessments with overlapping dates and shared assessors

- **Permission:** `assessments:create:all`
- **Body:** JSON — `ConflictCheckRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `assessmentId` | `string` |  |  |
| `assessorIds` | `string[]` |  |  |
| `startDate` | `string(date-time)` |  |  |
| `endDate` | `string(date-time)` |  |  |

### `GET /api/v1/assessments/export/csv`

**Export assessments to CSV.**
Export assessments to CSV format with same filters as search

- **Permission:** `assessments:read:all`, `assessments:read:org` (any of)
- **Query:** `applicationId`, `organizationId`, `assessmentTypeId`, `assessorId`, `status`, `name`

### `POST /api/v1/assessments/import`

**Import assessments from a CSV.**
Creates one assessment per row, all or nothing. Missing applications are created, and missing campaigns too when the caller has campaigns:create:all. notifyStakeholders sends the usual assessment-created notifications and email.

- **Permission:** `assessments:create:all`
- **Query:** `notifyStakeholders`
- **Body:** multipart/form-data
- **Returns:** `data` = `AssessmentImportResultDto`
- **Rule:** All or nothing — if any row is invalid, nothing is imported. Run the preview first and show a human the rows that create new targets.

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `POST /api/v1/assessments/import/preview`

**Preview an assessment CSV import.**
Dry run: resolves every row and reports what it would create and any errors. Writes nothing.

- **Permission:** `assessments:create:all`
- **Body:** multipart/form-data
- **Returns:** `data` = `AssessmentImportPreviewDto`
- **Rule:** Multipart `file`. Writes nothing. Every row needs a `client` column naming an existing client; rows naming an unknown target are flagged as creating one.

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `GET /api/v1/assessments/import/template`

**Download the assessment CSV import template.**
Every built-in column, one column per assessment custom-field variable, and an example row.

- **Permission:** `assessments:create:all`

### `GET /api/v1/assessments/metrics`

**Get assessment metrics.**
Get assessment statistics by status and past due count

- **Permission:** `assessments:read:all`, `assessments:read:org` (any of)
- **Query:** `organizationId`, `assessmentTypeIds`
- **Returns:** `data` = `AssessmentMetricsDto`

### `GET /api/v1/assessments/metrics/vulnerability-trend`

**Get vulnerability trend.**
Daily vulnerability counts per severity for a lifecycle event type (CREATED, CLOSED, REOPENED, SEVERITY_CHANGED), backed by a TimescaleDB continuous aggregate

- **Permission:** `assessments:read:all`, `assessments:read:org` (any of)
- **Query:** `eventType`, `days`, `organizationId`
- **Returns:** `data` = `ListVulnerabilityTrendPointDto`

### `GET /api/v1/assessments/summary`

**Assessment summary counts.**
Aggregate assessment counts (active / total) visible to the caller — a lightweight grouped query for the sidebar badge and dashboards (does not materialize the assessment list).

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:read:org`, `assessments:read:owned` (any of)
- **Returns:** `data` = `AssessmentSummaryDto`

### `DELETE /api/v1/assessments/{id}`

**Delete assessment.**
Soft delete an assessment

- **Permission:** `assessments:delete:all`, `assessments:delete:team` (any of)
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/assessments/{id}`

**Get assessment by ID.**
Retrieve a single assessment with full details including field definitions and values

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:read:org`, `assessments:read:owned` (any of)
- **Path:** `id`
- **Returns:** `data` = `AssessmentDto`

### `PUT /api/v1/assessments/{id}`

**Update assessment.**
Update assessment field values and/or status. Field values are validated against field definitions.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Body:** JSON — `UpdateAssessmentRequest`
- **Returns:** `data` = `AssessmentDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `applicationId` | `string` |  |  |
| `assessmentTypeId` | `string` |  |  |
| `moveToTypeWorkflow` | `boolean` |  |  |
| `campaignId` | `string` |  |  |
| `reportTemplateId` | `string` |  |  |
| `teamId` | `string` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `status` | `string` |  |  |
| `assessorId` | `string` |  |  |
| `assessorIds` | `string[]` |  |  |
| `engagementManagerId` | `string` |  |  |
| `remediationManagerId` | `string` |  |  |
| `completedDate` | `string(date-time)` |  |  |
| `startDate` | `string(date-time)` |  |  |
| `plannedEndDate` | `string(date-time)` |  |  |
| `scope` | `string` |  |  |
| `engagementUrls` | `EngagementUrlDto[]` |  |  |
| `stakeholders` | `StakeholderDto[]` |  |  |

### `GET /api/v1/assessments/{id}/assignable-assessors`

**List assignable assessors.**
The users who can be added as assessors on this assessment: the members of the assessment's team, or every internal user when the assessment has no team set. Gated on assessment access rather than users:read, so an assessor editing their own assessment can populate the picker without permission to browse the user directory.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Returns:** `data` = `ListAssignableUserDto`

### `GET /api/v1/assessments/{id}/events`

**Subscribe to assessment events.**
Opens a Server-Sent Events stream for real-time field lock/unlock events on the assessment.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Query:** `clientId`
- **Returns:** `data` = `SseEmitter`

### `DELETE /api/v1/assessments/{id}/fields/{fieldId}/lock`

**Release field lock.**
Releases the caller's edit lock on an assessment field.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`, `fieldId`
- **Returns:** `data` = `Void`

### `POST /api/v1/assessments/{id}/fields/{fieldId}/lock`

**Acquire field lock.**
Acquires an edit lock on an assessment field for collaborative editing. Returns 409 if another user holds the lock.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`, `fieldId`
- **Returns:** `data` = `Void`

### `POST /api/v1/assessments/{id}/files`

**Confirm file upload.**
Persists file metadata after a successful direct upload to MinIO.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Body:** JSON — `ConfirmUploadRequest`
- **Returns:** `data` = `AssessmentFileDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileId` | `string` | yes |  |
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `POST /api/v1/assessments/{id}/files/prepare`

**Allocate an upload target.**
Returns a file id and the backend URL to PUT the file body to. After the upload completes, call POST /{id}/files to confirm.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Body:** JSON — `PrepareUploadRequest`
- **Returns:** `data` = `UploadTargetResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `DELETE /api/v1/assessments/{id}/files/{fileId}`

**Delete a file.**
Deletes the file from storage and removes its metadata from the assessment.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`, `fileId`
- **Returns:** `data` = `Void`

### `GET /api/v1/assessments/{id}/files/{fileId}/content`

**Download an attachment.**
Streams the file's bytes as an attachment. Requires read access to the assessment.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned`, `assessments:read:org`, `assessments:read:owned` (any of)
- **Path:** `id`, `fileId`

### `PUT /api/v1/assessments/{id}/files/{fileId}/content`

**Upload an attachment's bytes.**
Streams the request body into storage under the prepared file id.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`, `fileId`
- **Query:** `fileName` (required)
- **Returns:** `data` = `Void`

### `POST /api/v1/assessments/{id}/move-workflow`

**Move an assessment to another workflow.**
Maps the assessment's status and each finding through the target workflow, and remaps stage completions by stage name. Pass dryRun to preview without writing anything.

- **Permission:** `config:write`
- **Path:** `id`
- **Body:** JSON — `MoveWorkflowRequest`
- **Returns:** `data` = `WorkflowMovePreviewDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `workflowId` | `string` | yes |  |
| `dryRun` | `boolean` |  |  |

### `POST /api/v1/assessments/{id}/report/generate`

**Generate assessment report.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `id`
- **Returns:** `data` = `AssessmentDto`
- **Rule:** The older, synchronous generator. Prefer `POST /reports/{assessmentId}/generate`, which is what the UI uses and produces the DOCX, the PDF and the encrypted PDF.

### `POST /api/v1/assessments/{id}/validate`

**Validate field values.**
Validate field values against the assessment's field definitions without saving

- **Permission:** `assessments:edit:all`
- **Path:** `id`
- **Body:** JSON
- **Returns:** `data` = `MapStringString`

## Vulnerabilities

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities`

**List vulnerabilities for an assessment.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`
- **Query:** `page`, `size`, `sort`
- **Returns:** `data` = `ListVulnerabilityDto`

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities`

**Create a vulnerability.**
Creates a vulnerability for the given assessment. Fails if the assessment is finalized.

- **Permission:** `vulnerabilities:create:all`, `vulnerabilities:create:team`, `vulnerabilities:create:assessment` (any of)
- **Path:** `assessmentId`
- **Body:** JSON — `CreateVulnerabilityRequest`
- **Returns:** `data` = `VulnerabilityDto`
- **Rule:** `severity` is the enum `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL`, whatever labels the UI shows. `fieldValues` is keyed by field id, like an assessment's `initialFieldValues`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `severity` | `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL` | yes |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |
| `cvssScore` | `number(double)` |  |  |
| `cvssString` | `string` |  |  |
| `assetLocation` | `string` |  |  |
| `description` | `string` |  |  |
| `recommendation` | `string` |  |  |
| `details` | `string` |  |  |
| `trackingId` | `string` |  |  |
| `order` | `integer(int32)` |  |  |
| `openedAt` | `string(date-time)` |  |  |
| `closedAt` | `string(date-time)` |  |  |
| `plannedRemediationDate` | `string(date-time)` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `vulnerabilityCategoryId` | `string` |  |  |
| `section` | `string` |  |  |

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/carry-forward/{sourceVulnerabilityId}`

**Add a finding from another assessment to this one.**
Copies a finding out of another assessment's history into this assessment. A source that is still open carries its opened date, status and remediation owner over unchanged — it is the same live issue, so its SLA clock keeps running. A source that was closed starts fresh, as a new occurrence of something that has come back. Either way the new finding records which one it came from, and the same so…

- **Permission:** `vulnerabilities:create:all`, `vulnerabilities:create:team`, `vulnerabilities:create:assessment` (any of)
- **Path:** `assessmentId`, `sourceVulnerabilityId`
- **Returns:** `data` = `VulnerabilityDto`

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities/export`

**Export an assessment's vulnerabilities as SARIF or CycloneDX.**
Renders every non-deleted finding on the assessment, most severe first. `format=sarif` produces SARIF 2.1.0, in which the assessment is the tool run and each finding is a result; `format=cyclonedx` produces CycloneDX 1.6, in which the application is the component and each finding is a vulnerability affecting it, carrying a VEX analysis state. Row-level scope is enforced in the service, so a calle…

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Path:** `assessmentId`
- **Query:** `format`

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/reorder`

**Reorder vulnerabilities within an assessment.**
Bulk-updates the order field. All provided IDs must belong to the given assessment.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`
- **Body:** JSON — `ReorderVulnerabilitiesRequest`
- **Returns:** `data` = `ListVulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `order` | `VulnerabilityOrderItem[]` | yes |  |

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/reorder/by-severity`

**Reset vulnerability order to severity order.**
Puts every finding back in severity order (Critical first), keeping the current order within each severity.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`
- **Returns:** `data` = `ListVulnerabilityDto`

### `DELETE /api/v1/assessments/{assessmentId}/vulnerabilities/{id}`

**Delete a vulnerability.**
Soft-deletes a vulnerability. Fails if the assessment is finalized.

- **Permission:** `vulnerabilities:delete:all`, `vulnerabilities:delete:team`, `vulnerabilities:delete:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities/{id}`

**Get a single vulnerability.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`
- **Returns:** `data` = `VulnerabilityDto`

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/{id}`

**Update a vulnerability.**
Partially updates a vulnerability. Fails if the assessment is finalized.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `UpdateVulnerabilityRequest`
- **Returns:** `data` = `VulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `severity` | `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL` |  |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |
| `cvssScore` | `number(double)` |  |  |
| `cvssString` | `string` |  |  |
| `assetLocation` | `string` |  |  |
| `description` | `string` |  |  |
| `recommendation` | `string` |  |  |
| `details` | `string` |  |  |
| `trackingId` | `string` |  |  |
| `order` | `integer(int32)` |  |  |
| `openedAt` | `string(date-time)` |  |  |
| `closedAt` | `string(date-time)` |  |  |
| `plannedRemediationDate` | `string(date-time)` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `vulnerabilityCategoryId` | `string` |  |  |
| `section` | `string` |  |  |

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/comments`

**Add a comment to a vulnerability.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `AddCommentRequest`
- **Returns:** `data` = `ListVulnerabilityCommentDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `content` | `string` | yes |  |

### `DELETE /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/comments/{commentId}`

**Delete a comment from a vulnerability.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`, `commentId`
- **Returns:** `data` = `ListVulnerabilityCommentDto`

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception`

**Update vulnerability exception data.**
Replaces the exception workflow data (number, dates, approval, state, justification). Allowed on finalized assessments. Null/blank values clear the stored field.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `UpdateVulnerabilityExceptionRequest`
- **Returns:** `data` = `VulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `exceptionNumber` | `string` |  |  |
| `exceptionStartDate` | `string(date-time)` |  |  |
| `exceptionApproval` | `string` |  |  |
| `exceptionState` | `string` |  |  |
| `exceptionExpiryDate` | `string(date-time)` |  |  |
| `exceptionJustification` | `string` |  |  |

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception-files`

**Confirm exception file upload.**
Persists file metadata after a successful direct upload to MinIO.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `ConfirmUploadRequest`
- **Returns:** `data` = `AssessmentFileDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileId` | `string` | yes |  |
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception-files/prepare`

**Allocate an upload target for an exception justification file.**
Returns a file id and the backend URL to PUT the file body to. After the upload completes, call POST /{id}/exception-files to confirm.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `PrepareUploadRequest`
- **Returns:** `data` = `UploadTargetResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `DELETE /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception-files/{fileId}`

**Delete an exception file.**
Deletes the file from storage and removes its metadata from the vulnerability.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`, `fileId`
- **Returns:** `data` = `Void`

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception-files/{fileId}/content`

**Download an exception file.**
Streams the file's bytes as an attachment download.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Path:** `assessmentId`, `id`, `fileId`

### `PUT /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/exception-files/{fileId}/content`

**Upload an exception file's bytes.**
Streams the request body into storage under the prepared file id.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`, `fileId`
- **Query:** `fileName` (required)
- **Returns:** `data` = `Void`

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/fields`

**Update vulnerability fields (post-finalization).**
Updates editable fields on a vulnerability. Allowed on finalized assessments. Appends an un-deletable system comment with before/after values for every changed field.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `UpdateVulnerabilityRequest`
- **Returns:** `data` = `VulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `severity` | `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL` |  |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |
| `cvssScore` | `number(double)` |  |  |
| `cvssString` | `string` |  |  |
| `assetLocation` | `string` |  |  |
| `description` | `string` |  |  |
| `recommendation` | `string` |  |  |
| `details` | `string` |  |  |
| `trackingId` | `string` |  |  |
| `order` | `integer(int32)` |  |  |
| `openedAt` | `string(date-time)` |  |  |
| `closedAt` | `string(date-time)` |  |  |
| `plannedRemediationDate` | `string(date-time)` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `vulnerabilityCategoryId` | `string` |  |  |
| `section` | `string` |  |  |

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/remediation-owner`

**Assign the remediation owner.**
Sets the user accountable for remediating this finding; a null or blank userId clears the assignment. Allowed on finalized assessments — a finding acquires an owner once it is open. Internal users only: an external account with vulnerability edit rights still gets 403. Assigning subscribes the owner to the finding's discussion.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `UpdateVulnerabilityRemediationOwnerRequest`
- **Returns:** `data` = `VulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` |  |  |

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/stage-completions`

**List remediation stage completions.**
Every configured remediation stage in order, with its completion when one exists. The terminal (last) stage reflects the vulnerability's own closedAt.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`
- **Returns:** `data` = `ListVulnerabilityStageCompletionDto`

### `DELETE /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/stage-completions/{stageId}`

**Clear a remediation stage completion.**
Removes the stage's completion event. Clearing the terminal stage reopens the vulnerability (status Open, closedAt cleared). No-op when nothing was recorded for the stage.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`, `stageId`
- **Returns:** `data` = `ListVulnerabilityStageCompletionDto`

### `PUT /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/stage-completions/{stageId}`

**Record a remediation stage completion.**
Marks the fix verified in the given stage. Completing the terminal (last configured) stage closes the vulnerability (status Closed + closedAt); earlier stages record a completion and leave the finding open. Stages complete in any order and never affect the SLA clock. Allowed on finalized assessments.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`, `stageId`
- **Returns:** `data` = `ListVulnerabilityStageCompletionDto`

### `PATCH /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/status`

**Update vulnerability status.**
Updates the lifecycle status of a vulnerability. Allowed on finalized assessments. Logs an un-deletable system comment when the vulnerability has been opened.

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `assessmentId`, `id`
- **Body:** JSON — `UpdateVulnerabilityStatusRequest`
- **Returns:** `data` = `VulnerabilityDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `status` | `string` | yes |  |

### `GET /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/subscribers`

**List the users following a vulnerability's discussion.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`
- **Returns:** `data` = `ListString`

### `DELETE /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/subscribers/{username}`

**Remove a user from a vulnerability's discussion.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`, `username`
- **Returns:** `data` = `ListString`

### `POST /api/v1/assessments/{assessmentId}/vulnerabilities/{id}/subscribers/{username}`

**Add a user to a vulnerability's discussion.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:comment:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:comment:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`, `id`, `username`
- **Returns:** `data` = `ListString`

## Vulnerabilities (Global)

### `GET /api/v1/vulnerabilities`

**List vulnerabilities across assessments.**
Returns a paginated, filterable list of opened vulnerabilities across every assessment the caller is authorized to read, each with its application, assessment, and organization names. Filters: `search`, `severities`, `organizationIds`, `applicationId`, `assessmentId`, `statuses`, `includeClosed` (open-only by default), and an `openedFrom`/`openedTo` date range over when each vulnerability was ope…

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `page`, `size`, `sort`, `search`, `severities`, `organizationIds`, `applicationId`, `assessmentId`, `statuses`, `includeClosed`, `openedFrom`, `openedTo`
- **Returns:** `data` = `ListVulnerabilityListDto`

### `GET /api/v1/vulnerabilities/export.csv`

**Export the filtered vulnerabilities list to CSV.**
The same scoped, filtered, sorted rows as `GET /vulnerabilities`, unpaginated, as CSV. Accepts the identical filters — `search`, `severities`, `organizationIds`, `applicationId`, `assessmentId`, `statuses`, `includeClosed`, `openedFrom`/`openedTo` — plus `sort`.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `sort`, `search`, `severities`, `organizationIds`, `applicationId`, `assessmentId`, `statuses`, `includeClosed`, `openedFrom`, `openedTo`

### `GET /api/v1/vulnerabilities/summary`

**SLA-aware vulnerability summary.**
Per-severity counts of opened vulnerabilities — total, SLA-tracked, past-due, on-time, recently closed, open (non-exception), and exceptions — aggregated across every assessment the caller is authorized to read. Narrowed within the caller's scope by the same filters `GET /vulnerabilities` accepts — `applicationId`, `organizationId`, `subOrganizationId`, `assessmentId`, `severities`, `statuses`, `…

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `applicationId`, `organizationIds`, `subOrganizationId`, `assessmentId`, `severities`, `statuses`, `search`, `openedFrom`, `openedTo`
- **Returns:** `data` = `VulnerabilitySummaryDto`

### `GET /api/v1/vulnerabilities/{id}`

**Get one vulnerability by id (cross-assessment).**
Resolves a single vulnerability by id alone — no assessment id required — limited to what the caller is authorized to read, with its application, assessment, and organization names. Returns 404 if the vulnerability does not exist or is outside the caller's scope.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Path:** `id`
- **Returns:** `data` = `VulnerabilityListDto`

## Retests

### `GET /api/v1/assessments/{assessmentId}/retests`

**List retests for an assessment.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`
- **Returns:** `data` = `ListRetestDto`

### `POST /api/v1/assessments/{assessmentId}/retests`

**Create a retest for a vulnerability in an assessment.**

- **Permission:** `vulnerabilities:create:all`, `vulnerabilities:create:team`, `vulnerabilities:create:assessment`, `vulnerabilities:retest:org`, `vulnerabilities:retest:owned` (any of)
- **Path:** `assessmentId`
- **Body:** JSON — `CreateRetestRequest`
- **Returns:** `data` = `RetestDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `vulnerabilityId` | `string` | yes |  |
| `scheduledStartDate` | `string(date-time)` |  |  |
| `scheduledEndDate` | `string(date-time)` |  |  |
| `assignedAssessorIds` | `string[]` |  |  |
| `scope` | `string` |  |  |
| `comment` | `string` |  |  |

### `GET /api/v1/retests`

**List all retests, optionally filtered to those assigned to the current user and/or by status (comma-separated, e.g. REQUESTED,SCHEDULED,IN_PROGRESS).**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:retest:owned` (any of)
- **Query:** `assignedToMe`, `status`
- **Returns:** `data` = `ListRetestDto`

### `GET /api/v1/retests/calendar`

**List retests within a date range for calendar display.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:retest:owned` (any of)
- **Query:** `startDate` (required), `endDate` (required)
- **Returns:** `data` = `ListRetestDto`

### `DELETE /api/v1/retests/{id}`

**Cancel a retest.**
Moves the retest to CANCELLED and records the cancellation on the vulnerability — it stays on the finding's record rather than being removed, and drops out of the remediation queue because that shows open retests only. Available to staff and to the app owners who can request a retest, limited to the assessments the caller may read. A completed retest is history and cannot be cancelled.

- **Permission:** `vulnerabilities:delete:all`, `vulnerabilities:delete:team`, `vulnerabilities:delete:assessment`, `vulnerabilities:retest:org`, `vulnerabilities:retest:owned` (any of)
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/retests/{id}`

**Get a single retest by ID.**

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:assessment`, `vulnerabilities:read:org`, `vulnerabilities:retest:org`, `vulnerabilities:read:owned`, `vulnerabilities:retest:owned` (any of)
- **Path:** `id`
- **Returns:** `data` = `RetestDto`

### `PATCH /api/v1/retests/{id}`

**Partially update a retest.**

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `id`
- **Body:** JSON — `UpdateRetestRequest`
- **Returns:** `data` = `RetestDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `scheduledStartDate` | `string(date-time)` |  |  |
| `scheduledEndDate` | `string(date-time)` |  |  |
| `assignedAssessorIds` | `string[]` |  |  |
| `scope` | `string` |  |  |
| `comment` | `string` |  |  |
| `status` | `string` |  |  |
| `severity` | `string` |  |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |

### `POST /api/v1/retests/{id}/complete`

**Complete a retest with a PASS or FAIL result.**

- **Permission:** `vulnerabilities:edit:all`, `vulnerabilities:edit:team`, `vulnerabilities:edit:assessment` (any of)
- **Path:** `id`
- **Body:** JSON — `CompleteRetestRequest`
- **Returns:** `data` = `RetestDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `result` | `string` | yes |  |
| `comment` | `string` |  |  |
| `closure` | `string` |  | What a passing retest closes: RETEST_ONLY or a configured remediation stage id (legacy DEVELOPMENT/STAGING/PR… |
| `severity` | `string` |  |  |
| `likelihood` | `string` |  |  |
| `impact` | `string` |  |  |

## Peer Reviews

### `GET /api/v1/assessments/{assessmentId}/peer-reviews`

**Get peer review history for an assessment.**

- **Permission:** `peerreview:read:all`, `peerreview:read:team` (any of)
- **Path:** `assessmentId`
- **Returns:** `data` = `ListPeerReviewDto`

### `POST /api/v1/assessments/{assessmentId}/peer-reviews/submit`

**Submit an assessment for peer review.**

- **Permission:** `peerreview:create:all`, `peerreview:create:assessment` (any of)
- **Path:** `assessmentId`
- **Returns:** `data` = `PeerReviewDto`

### `GET /api/v1/peer-reviews/queue`

**Get paginated peer review queue (PENDING + IN_REVIEW).**

- **Permission:** `peerreview:read:all`, `peerreview:read:team` (any of)
- **Query:** `page`, `size`, `sort`
- **Returns:** `data` = `ListPeerReviewDto`

### `GET /api/v1/peer-reviews/{reviewId}`

**Get a single peer review by ID.**

- **Permission:** `peerreview:read:all`, `peerreview:read:team` (any of)
- **Path:** `reviewId`
- **Returns:** `data` = `PeerReviewDto`

### `PUT /api/v1/peer-reviews/{reviewId}`

**Save reviewer edits and notes.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`
- **Body:** JSON — `UpdatePeerReviewRequest`
- **Returns:** `data` = `PeerReviewDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `revisedFieldValues` | `map<string, string>` |  |  |
| `fieldNotes` | `map<string, string>` |  |  |
| `vulnerabilities` | `PeerReviewVulnerabilityDto[]` |  |  |

### `POST /api/v1/peer-reviews/{reviewId}/accept`

**Assessor accepts selected changes and closes the review.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`
- **Body:** JSON — `AcceptPeerReviewRequest`
- **Returns:** `data` = `PeerReviewDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `acceptedAssessmentFieldIds` | `string[]` |  |  |
| `acceptedVulnerabilityChanges` | `map<string, string[]>` |  |  |

### `POST /api/v1/peer-reviews/{reviewId}/complete`

**Reviewer marks review as done.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`
- **Returns:** `data` = `PeerReviewDto`

### `GET /api/v1/peer-reviews/{reviewId}/events`

**Subscribe to peer review events.**
Opens a Server-Sent Events stream carrying per-editor lock/unlock events for the review, so two reviewers working it at once each see which regions the other is in.

- **Permission:** `peerreview:read:all`, `peerreview:read:team` (any of)
- **Path:** `reviewId`
- **Query:** `clientId`
- **Returns:** `data` = `SseEmitter`

### `DELETE /api/v1/peer-reviews/{reviewId}/fields/{fieldId}/lock`

**Release an editor lock.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`, `fieldId`
- **Returns:** `data` = `Void`

### `POST /api/v1/peer-reviews/{reviewId}/fields/{fieldId}/lock`

**Acquire an editor lock.**
Takes or refreshes the caller's lock on one editable region of the review. Returns 409 when another active user holds it. The region id is an opaque client-built key, so the lock is exactly as narrow as the editor being typed into.

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`, `fieldId`
- **Returns:** `data` = `Void`

### `POST /api/v1/peer-reviews/{reviewId}/reject`

**Assessor rejects the entire review and unlocks the assessment.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`
- **Returns:** `data` = `PeerReviewDto`

### `POST /api/v1/peer-reviews/{reviewId}/start`

**Reviewer claims the review.**

- **Permission:** `peerreview:edit:all`, `peerreview:edit:team` (any of)
- **Path:** `reviewId`
- **Returns:** `data` = `PeerReviewDto`

## Remediation

### `GET /api/v1/remediation/export.csv`

**Export the remediation queue to CSV.**
The same scoped, filtered, sorted rows as `GET /remediation/queue`, unpaginated, as CSV. Accepts the identical filters — `search`, `severities`, `organizationIds`, `applicationIds`, `assessmentIds`, `statuses`, `type`, `includeCompletedRetests` — plus `sort`. Retest rows carry three extra columns the table has no room for: completed date, result, and who verified it, for reporting on retests comp…

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `sort`, `search`, `severities`, `organizationIds`, `applicationIds`, `assessmentIds`, `statuses`, `type`, `buckets`, `includeCompletedRetests`

### `GET /api/v1/remediation/queue`

**List the remediation queue.**
Returns a page of items requiring remediation: open tracked vulnerabilities at or past their SLA warning threshold, interleaved with requested, scheduled, and in-progress retests. Items are ordered past-due (urgent) first, then approaching-deadline (warning), then not-yet-due; within each group by due date — a vulnerability's SLA deadline or a retest's scheduled end date. Results are limited to t…

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `page`, `size`, `sort`, `search`, `severities`, `organizationIds`, `applicationIds`, `assessmentIds`, `statuses`, `type`, `buckets`, `includeCompletedRetests`
- **Returns:** `data` = `ListRemediationRowDto`

### `GET /api/v1/remediation/queue-count`

**Get the number of items in the remediation queue.**
Counts open tracked vulnerabilities at or past their SLA warning threshold plus requested/scheduled/in-progress retests — the rows the remediation queue shows.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team` (any of)
- **Returns:** `data` = `Long`

### `GET /api/v1/remediation/queue-summary`

**Break the remediation queue down into its badge buckets.**
Counts the caller's remediation queue per bucket — past due, due soon, and retests requested / scheduled / in progress — under the same scope and filters as `GET /remediation/queue` (`search`, `severities`, `organizationIds`, `applicationIds`, `assessmentIds`, `statuses`, `type`). The buckets add up to `total`.

- **Permission:** `vulnerabilities:read:all`, `vulnerabilities:read:team`, `vulnerabilities:read:org`, `vulnerabilities:read:owned` (any of)
- **Query:** `search`, `severities`, `organizationIds`, `applicationIds`, `assessmentIds`, `statuses`, `type`
- **Returns:** `data` = `RemediationQueueSummaryDto`

## Notebook

### `GET /api/v1/applications/{appId}/notebook`

**Get notebook tree.**
Returns the full notebook node tree for an application.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned` (any of)
- **Path:** `appId`
- **Returns:** `data` = `ListNotebookNodeDto`

### `POST /api/v1/applications/{appId}/notebook/nodes`

**Create notebook node.**
Creates a new notebook node (folder or note) under an application, optionally nested beneath a parent node.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `appId`
- **Body:** JSON — `CreateNotebookNodeRequest`
- **Returns:** `data` = `NotebookNodeDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | `string` | yes |  |
| `content` | `string` |  |  |
| `parentId` | `string` |  |  |
| `orderIndex` | `integer(int32)` |  |  |

### `GET /api/v1/applications/{appId}/notebook/search`

**Search notebook nodes.**
Searches an application's notebook by text query, author, and/or creation date range.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned` (any of)
- **Path:** `appId`
- **Query:** `q`, `createdById`, `from`, `to`
- **Returns:** `data` = `ListNotebookSearchResultDto`

### `DELETE /api/v1/notebook/nodes/{nodeId}`

**Delete notebook node.**
Deletes a notebook node and all of its descendants and attachments.

- **Permission:** `assessments:delete:all`
- **Path:** `nodeId`
- **Returns:** `data` = `Void`

### `GET /api/v1/notebook/nodes/{nodeId}`

**Get notebook node.**
Returns a single notebook node including its content and attachments.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned` (any of)
- **Path:** `nodeId`
- **Returns:** `data` = `NotebookNodeDto`

### `PUT /api/v1/notebook/nodes/{nodeId}`

**Update notebook node.**
Updates a notebook node's title and/or content.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`
- **Body:** JSON — `UpdateNotebookNodeRequest`
- **Returns:** `data` = `NotebookNodeDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | `string` |  |  |
| `content` | `string` |  |  |
| `orderIndex` | `integer(int32)` |  |  |

### `POST /api/v1/notebook/nodes/{nodeId}/files/confirm`

**Confirm notebook file upload.**
Persists attachment metadata after a successful direct upload to storage.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`
- **Body:** JSON — `ConfirmUploadRequest`
- **Returns:** `data` = `NotebookAttachmentDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileId` | `string` | yes |  |
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `POST /api/v1/notebook/nodes/{nodeId}/files/prepare`

**Allocate an upload target for a notebook file.**
Returns a file id and the backend URL to PUT the file body to. After the upload completes, call the confirm endpoint.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`
- **Body:** JSON — `PrepareUploadRequest`
- **Returns:** `data` = `UploadTargetResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `fileName` | `string` | yes |  |
| `contentType` | `string` | yes |  |
| `fileSize` | `integer(int64)` |  |  |

### `DELETE /api/v1/notebook/nodes/{nodeId}/files/{fileId}`

**Delete notebook file.**
Deletes the attachment from storage and removes its metadata from the node.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`, `fileId`
- **Returns:** `data` = `Void`

### `GET /api/v1/notebook/nodes/{nodeId}/files/{fileId}/content`

**Download a notebook attachment.**
Streams the attachment's bytes as an attachment download.

- **Permission:** `assessments:read:all`, `assessments:read:team`, `assessments:read:assigned` (any of)
- **Path:** `nodeId`, `fileId`

### `PUT /api/v1/notebook/nodes/{nodeId}/files/{fileId}/content`

**Upload a notebook attachment's bytes.**
Streams the request body into storage under the prepared file id.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`, `fileId`
- **Query:** `fileName` (required)
- **Returns:** `data` = `Void`

### `PUT /api/v1/notebook/nodes/{nodeId}/move`

**Move notebook node.**
Moves a notebook node to a new parent and/or position within the tree.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `nodeId`
- **Body:** JSON — `MoveNotebookNodeRequest`
- **Returns:** `data` = `NotebookNodeDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `newParentId` | `string` |  |  |
| `newOrderIndex` | `integer(int32)` |  |  |

## Assessment Checklists

### `GET /api/v1/assessments/{assessmentId}/checklists`

**Get checklists for an assessment.**

- **Permission:** any signed-in user
- **Path:** `assessmentId`
- **Returns:** `data` = `ListAssessmentChecklistDto`

### `POST /api/v1/assessments/{assessmentId}/checklists`

**Add a checklist to an assessment.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned`, `assessments:edit:self` (any of)
- **Path:** `assessmentId`
- **Body:** JSON — `AddAssessmentChecklistRequest`
- **Returns:** `data` = `AssessmentChecklistDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `templateId` | `string` | yes |  |

### `DELETE /api/v1/assessments/{assessmentId}/checklists/{checklistId}`

**Remove a checklist from an assessment.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned`, `assessments:edit:self` (any of)
- **Path:** `assessmentId`, `checklistId`

### `PUT /api/v1/assessments/{assessmentId}/checklists/{checklistId}`

**Update checklist responses.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned`, `assessments:edit:self` (any of)
- **Path:** `assessmentId`, `checklistId`
- **Body:** JSON — `UpdateAssessmentChecklistRequest`
- **Returns:** `data` = `AssessmentChecklistDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `responses` | `ChecklistResponseDto[]` |  |  |

## Assessment Surveys

### `GET /api/v1/assessments/{assessmentId}/surveys`

**Get surveys for an assessment.**

- **Permission:** any signed-in user
- **Path:** `assessmentId`
- **Returns:** `data` = `ListAssessmentSurveyDto`

### `POST /api/v1/assessments/{assessmentId}/surveys`

**Add a survey to an assessment.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:self` (any of)
- **Path:** `assessmentId`
- **Body:** JSON — `AddAssessmentSurveyRequest`
- **Returns:** `data` = `AssessmentSurveyDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `templateId` | `string` | yes |  |

### `DELETE /api/v1/assessments/{assessmentId}/surveys/{surveyId}`

**Remove a survey from an assessment.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:self` (any of)
- **Path:** `assessmentId`, `surveyId`

### `PUT /api/v1/assessments/{assessmentId}/surveys/{surveyId}`

**Update survey responses.**

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:self`, `survey:complete` (any of)
- **Path:** `assessmentId`, `surveyId`
- **Body:** JSON — `UpdateAssessmentSurveyRequest`
- **Returns:** `data` = `AssessmentSurveyDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `responses` | `SurveyResponseDto[]` |  |  |
| `complete` | `boolean` |  |  |

## Inline Images

### `POST /api/v1/assessments/{assessmentId}/inline-images`

**Upload an inline image.**
Uploads an image for an assessment's rich-text field and returns a short link URL to embed in the editor.

- **Permission:** `assessments:edit:all`, `assessments:edit:team`, `assessments:edit:assigned` (any of)
- **Path:** `assessmentId`
- **Body:** multipart/form-data

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `POST /api/v1/inline-images/library`

**Upload a reusable template image.**
Uploads an image for a content template or default vulnerability. Unlike an assessment's images it is not scoped to one engagement — it is readable by any authenticated user, and is copied into an assessment when the template is used there, so the assessment's copy cannot change underneath it afterwards.

- **Permission:** `content-templates:create`, `content-templates:edit`, `default-vulnerabilities:create`, `default-vulnerabilities:edit` (any of)
- **Body:** multipart/form-data

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `GET /api/v1/inline-images/{imageId}`

**Serve an inline image.**
Streams the image bytes. Requires an authenticated caller with read access to the owning assessment. Returns 404 if the image does not exist.

- **Permission:** any signed-in user
- **Path:** `imageId`

## Assessment Workflow Config

### `GET /api/v1/config/assessment-workflow`

**Get assessment workflow configuration.**
Retrieve Default Workflow's statuses and settings. Kept for one release as the alias for Default Workflow.

- **Permission:** any signed-in user
- **Returns:** `data` = `AssessmentWorkflow`

### `PUT /api/v1/config/assessment-workflow`

**Update assessment workflow configuration.**
Replace Default Workflow's statuses and settings. Its id, name and default and archived flags are not changed. Kept for one release as the alias for Default Workflow.

- **Permission:** `config:write`
- **Body:** JSON — `AssessmentWorkflow`
- **Returns:** `data` = `AssessmentWorkflow`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `string` |  |  |
| `name` | `string` |  |  |
| `defaultWorkflow` | `boolean` |  |  |
| `archived` | `boolean` |  |  |
| `statuses` | `string[]` |  |  |
| `newAssessmentStatus` | `string` |  |  |
| `inProgressStatus` | `string` |  |  |
| `completedStatus` | `string` |  |  |
| `statusColors` | `map<string, string>` |  |  |
| `vulnerabilitySlas` | `VulnerabilitySla[]` |  |  |
| `vulnerabilityStatuses` | `string[]` |  |  |
| `remediationStages` | `RemediationStage[]` |  |  |
| `allowSelfPeerReview` | `boolean` |  |  |
| `createdAt` | `string(date-time)` |  |  |
| `updatedAt` | `string(date-time)` |  |  |

### `POST /api/v1/config/assessment-workflow/recalculate-sla`

**Recalculate stored SLA due dates.**
Recalculate every open finding's stored due and warning dates from the current SLAs in the background, and return findings no longer past due to Open. Use it to repair due dates after a recalculation failed.

- **Permission:** `config:write`
- **Returns:** `data` = `Void`
