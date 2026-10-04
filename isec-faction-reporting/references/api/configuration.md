# Assessment types, workflows and installation settings

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Assessment Types](#assessment-types), [Workflows](#workflows), [Region Config](#region-config), [Edition](#edition), [Status](#status)

## Assessment Types

### `GET /api/v1/assessment-types`

**Get all assessment types.**
Retrieves all assessment types with pagination support and optional search by name or description (case-insensitive)

- **Permission:** any signed-in user
- **Query:** `page`, `size`, `sort`, `search`
- **Returns:** `data` = `ListAssessmentTypeDto`

### `POST /api/v1/assessment-types`

**Create assessment type.**
Creates a new assessment type. Requires super_admin or assessments:create:all permission.

- **Permission:** `assessments:create:all`
- **Body:** JSON — `CreateAssessmentTypeRequest`
- **Returns:** `data` = `AssessmentTypeDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` | yes |  |
| `active` | `boolean` | yes |  |
| `workflowId` | `string` |  |  |

### `DELETE /api/v1/assessment-types/{id}`

**Delete or deactivate assessment type.**
Deletes an assessment type if not in use, otherwise deactivates it. Requires super_admin or assessments:delete:all permission.

- **Permission:** `assessments:delete:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/assessment-types/{id}`

**Get assessment type by ID.**
Retrieves a specific assessment type by its ID

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `AssessmentTypeDto`

### `PUT /api/v1/assessment-types/{id}`

**Update assessment type.**
Updates an existing assessment type. Requires super_admin or assessments:edit:all permission.

- **Permission:** `assessments:edit:all`
- **Path:** `id`
- **Body:** JSON — `UpdateAssessmentTypeRequest`
- **Returns:** `data` = `AssessmentTypeDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` | yes |  |
| `active` | `boolean` | yes |  |
| `workflowId` | `string` |  |  |

## Workflows

### `GET /api/v1/workflows`

**List workflows.**
Default Workflow first, then the others by name; archived ones only when asked.

- **Permission:** any signed-in user
- **Query:** `includeArchived`
- **Returns:** `data` = `ListWorkflowDto`

### `POST /api/v1/workflows`

**Create a workflow as a copy.**
Creates a new workflow copied from an existing one, with fresh stage ids that inherit the source stages' email settings.

- **Permission:** `config:write`
- **Body:** JSON — `CreateWorkflowRequest`
- **Returns:** `data` = `WorkflowDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `sourceWorkflowId` | `string` | yes |  |
| `name` | `string` | yes |  |

### `GET /api/v1/workflows/usage`

**Workflow usage counts.**
Every workflow's assessment type and assessment counts, archived workflows included.

- **Permission:** `config:write`
- **Returns:** `data` = `ListWorkflowUsageDto`

### `DELETE /api/v1/workflows/{id}`

**Delete a workflow.**
Deletes an unused workflow, with all of its rename records and its stages' email settings.

- **Permission:** `config:write`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/workflows/{id}`

**Get a workflow.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `WorkflowDto`

### `PUT /api/v1/workflows/{id}`

**Update a workflow.**

- **Permission:** `config:write`
- **Path:** `id`
- **Body:** JSON — `UpdateWorkflowRequest`
- **Returns:** `data` = `WorkflowDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `statuses` | `NamedEntry[]` |  |  |
| `newAssessmentStatus` | `string` |  |  |
| `inProgressStatus` | `string` |  |  |
| `completedStatus` | `string` |  |  |
| `statusColors` | `map<string, string>` |  |  |
| `vulnerabilitySlas` | `VulnerabilitySla[]` |  |  |
| `vulnerabilityStatuses` | `NamedEntry[]` |  |  |
| `remediationStages` | `RemediationStage[]` |  |  |
| `allowSelfPeerReview` | `boolean` |  |  |

### `POST /api/v1/workflows/{id}/archive`

**Archive a workflow.**
Hides a workflow from pickers; its assessments keep working. Refused for Default Workflow or while an assessment type uses it.

- **Permission:** `config:write`
- **Path:** `id`
- **Returns:** `data` = `WorkflowDto`

### `POST /api/v1/workflows/{id}/unarchive`

**Unarchive a workflow.**

- **Permission:** `config:write`
- **Path:** `id`
- **Returns:** `data` = `WorkflowDto`

## Region Config

### `GET /api/v1/config/regions`

**Get regions.**
Retrieve the configured list of application regions.

- **Permission:** any signed-in user
- **Returns:** `data` = `ListString`

### `PUT /api/v1/config/regions`

**Update regions.**
Replace the configured list of application regions. Only accessible to Super Admins.

- **Permission:** any signed-in user
- **Body:** JSON
- **Returns:** `data` = `ListString`

## Edition

### `GET /api/v1/edition`

**Get edition status.**
Feature availability, quota limits and current usage for this build.

- **Permission:** any signed-in user
- **Returns:** `data` = `EditionStatusDto`

## Status

### `GET /api/v1/status`

**Get service status.**
Public endpoint reporting the deployed version and uptime. No authentication required.

- **Permission:** none — public, no token needed
