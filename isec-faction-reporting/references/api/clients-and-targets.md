# Clients (organizations), targets (applications) and campaigns

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Organizations](#organizations), [Sub-Organizations](#sub-organizations), [Applications](#applications), [Application Connections](#application-connections), [Client Images](#client-images), [Application ID Configuration](#application-id-configuration), [Campaigns](#campaigns)

## Organizations

### `GET /api/v1/organizations`

**Get all organizations.**
Retrieve all organizations with pagination and optional search.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Query:** `page`, `size`, `sort`, `search`

### `POST /api/v1/organizations`

**Create a new organization.**
Create a new organization with specified details.

- **Permission:** `organizations:create:all`
- **Body:** JSON — `CreateOrganizationRequest`
- **Returns:** `data` = `OrganizationDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `distributionList` | `ClientContactDto[]` |  |  |

### `DELETE /api/v1/organizations/{id}`

**Delete an organization.**
Delete an organization. Cannot delete if applications are assigned.

- **Permission:** `organizations:delete:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/organizations/{id}`

**Get organization by ID.**
Retrieve a specific organization by its ID.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Path:** `id`
- **Returns:** `data` = `OrganizationDto`

### `PUT /api/v1/organizations/{id}`

**Update an existing organization.**
Update an existing organization's details.

- **Permission:** `organizations:edit:all`, `organizations:read:owned` (any of)
- **Path:** `id`
- **Body:** JSON — `UpdateOrganizationRequest`
- **Returns:** `data` = `OrganizationDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |
| `fieldValues` | `map<string, string>` |  |  |
| `remediationOwnerIds` | `string[]` |  |  |
| `distributionList` | `ClientContactDto[]` |  |  |

### `GET /api/v1/organizations/{id}/users`

**Get assigned users for organization.**

- **Permission:** `organizations:edit:all`
- **Path:** `id`
- **Returns:** `data` = `ListAssignedUserDto`

### `POST /api/v1/organizations/{id}/users`

**Assign a user to organization.**

- **Permission:** `organizations:edit:all`
- **Path:** `id`
- **Body:** JSON — `AssignUserRequest`
- **Returns:** `data` = `AssignedUserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` | yes |  |
| `accessLevel` | `string` | yes |  |

### `DELETE /api/v1/organizations/{id}/users/{userId}`

**Remove assigned user from organization.**

- **Permission:** `organizations:edit:all`
- **Path:** `id`, `userId`
- **Returns:** `data` = `Void`

### `PUT /api/v1/organizations/{id}/users/{userId}`

**Update assigned user access level.**

- **Permission:** `organizations:edit:all`
- **Path:** `id`, `userId`
- **Body:** JSON — `UpdateAssignedUserRequest`
- **Returns:** `data` = `AssignedUserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `accessLevel` | `string` | yes |  |

## Sub-Organizations

### `GET /api/v1/organizations/{organizationId}/sub-organizations`

**List an organization's sub-organizations.**
Returns the organization's divisions, each with the number of applications attributed to it.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Path:** `organizationId`
- **Returns:** `data` = `ListSubOrganizationDto`

### `POST /api/v1/organizations/{organizationId}/sub-organizations`

**Add a sub-organization.**
Creates a division within the organization. Names are unique per organization.

- **Permission:** `organizations:create:all`, `organizations:edit:all` (any of)
- **Path:** `organizationId`
- **Body:** JSON — `Request`
- **Returns:** `data` = `SubOrganizationDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |

### `DELETE /api/v1/organizations/{organizationId}/sub-organizations/{id}`

**Delete a sub-organization.**
Refused while applications are still attributed to it — reassign them first.

- **Permission:** `organizations:delete:all`, `organizations:edit:all` (any of)
- **Path:** `organizationId`, `id`
- **Returns:** `data` = `MapStringString`

### `PUT /api/v1/organizations/{organizationId}/sub-organizations/{id}`

**Rename a sub-organization.**

- **Permission:** `organizations:edit:all`
- **Path:** `organizationId`, `id`
- **Body:** JSON — `Request`
- **Returns:** `data` = `SubOrganizationDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |

### `GET /api/v1/sub-organizations`

**List sub-organizations across organizations.**
Returns every division the caller can see, each with its owning organization id and name plus the number of applications attributed to it. Pass `name` to look a division up by name — names are unique per organization, so a name shared by two organizations returns both.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Query:** `name`
- **Returns:** `data` = `ListSubOrganizationDto`

## Applications

### `GET /api/v1/applications`

**Get all applications.**
Retrieve all applications with pagination and optional search.

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org` (any of)
- **Query:** `page`, `size`, `search`, `sort`, `organizationId`, `subOrganizationId`, `status`

### `POST /api/v1/applications`

**Create a new application.**
Create a new application with specified details.

- **Permission:** `applications:create:all`, `applications:create:owned`, `applications:create:org` (any of)
- **Body:** JSON — `CreateApplicationRequest`
- **Returns:** `data` = `ApplicationDto`
- **Rule:** `organizationId` (the client) is required — the server refuses a target with no client, even though the schema does not mark it. Each entry in `urls` needs both `url` and `title`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `appId` | `string` |  |  |
| `description` | `string` |  |  |
| `urls` | `ApplicationUrlDto[]` |  |  |
| `stakeHolders` | `StakeholderDto[]` |  |  |
| `technologies` | `string[]` |  |  |
| `appOwner` | `AppOwnerDto` |  |  |
| `ownerName` | `string` |  |  |
| `ownerEmail` | `string` |  |  |
| `status` | `PRODUCTION|DEVELOPMENT|STAGING|TESTING|DECOMMISSIONED|PLANNED` |  |  |
| `organizationId` | `string` | yes |  |
| `subOrganizationId` | `string` |  |  |
| `region` | `string` |  |  |
| `applicationType` | `string` |  |  |
| `assessmentFrequency` | `string` |  |  |
| `customFrequencyMonths` | `integer(int32)` |  |  |
| `lastAssessmentDate` | `string(date-time)` |  |  |
| `fieldValues` | `map<string, string>` |  |  |

### `POST /api/v1/applications/import`

**Sync applications from a CSV.**
Upserts one application per row: matched by appId, then by name, and inserted when neither matches. Organizations and sub-organizations named in a row are created if they don't exist. Rows that fail are reported with their line number; the rest are still applied.

- **Permission:** any signed-in user
- **Body:** multipart/form-data
- **Returns:** `data` = `ApplicationImportResultDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `GET /api/v1/applications/import/template`

**Download the application CSV template.**
The column layout the sync accepts, with one example row filled in.

- **Permission:** any signed-in user

### `GET /api/v1/applications/organization/{organizationId}`

**Get applications by organization.**
Retrieve all applications for a specific organization. Only accessible to Super Admins.

- **Permission:** any signed-in user
- **Path:** `organizationId`
- **Returns:** `data` = `ListApplicationDto`

### `DELETE /api/v1/applications/{id}`

**Delete an application.**
Delete an application.

- **Permission:** `applications:delete:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/applications/{id}`

**Get application by ID.**
Retrieve a specific application by its ID.

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org` (any of)
- **Path:** `id`
- **Returns:** `data` = `ApplicationDto`

### `PUT /api/v1/applications/{id}`

**Update an existing application.**
Update an existing application's details.

- **Permission:** `applications:edit:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:org` (any of)
- **Path:** `id`
- **Body:** JSON — `UpdateApplicationRequest`
- **Returns:** `data` = `ApplicationDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `appId` | `string` |  |  |
| `description` | `string` |  |  |
| `urls` | `ApplicationUrlDto[]` |  |  |
| `stakeHolders` | `StakeholderDto[]` |  |  |
| `technologies` | `string[]` |  |  |
| `appOwner` | `AppOwnerDto` |  |  |
| `ownerName` | `string` |  |  |
| `ownerEmail` | `string` |  |  |
| `status` | `PRODUCTION|DEVELOPMENT|STAGING|TESTING|DECOMMISSIONED|PLANNED` |  |  |
| `organizationId` | `string` |  |  |
| `subOrganizationId` | `string` |  |  |
| `region` | `string` |  |  |
| `applicationType` | `string` |  |  |
| `assessmentFrequency` | `string` |  |  |
| `customFrequencyMonths` | `integer(int32)` |  |  |
| `lastAssessmentDate` | `string(date-time)` |  |  |
| `fieldValues` | `map<string, string>` |  |  |

### `POST /api/v1/applications/{id}/comments`

**Add a comment to an application.**

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:all`, `applications:edit:org` (any of)
- **Path:** `id`
- **Body:** JSON — `AddCommentRequest`
- **Returns:** `data` = `ListApplicationCommentDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `content` | `string` | yes |  |

### `DELETE /api/v1/applications/{id}/comments/{commentId}`

**Delete a comment from an application.**

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:all`, `applications:edit:org` (any of)
- **Path:** `id`, `commentId`
- **Returns:** `data` = `ListApplicationCommentDto`

### `PUT /api/v1/applications/{id}/move/{newOrganizationId}`

**Move application to another organization.**
Move an application from one organization to another. Only accessible to Super Admins.

- **Permission:** any signed-in user
- **Path:** `id`, `newOrganizationId`
- **Returns:** `data` = `ApplicationDto`

### `GET /api/v1/applications/{id}/subscribers`

**List the users following an application's discussion.**

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:all`, `applications:edit:org` (any of)
- **Path:** `id`
- **Returns:** `data` = `ListString`

### `DELETE /api/v1/applications/{id}/subscribers/{username}`

**Remove a user from an application's discussion.**

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:all`, `applications:edit:org` (any of)
- **Path:** `id`, `username`
- **Returns:** `data` = `ListString`

### `POST /api/v1/applications/{id}/subscribers/{username}`

**Add a user to an application's discussion.**

- **Permission:** `applications:read:all`, `applications:read:owned`, `applications:read:org`, `applications:edit:all`, `applications:edit:org` (any of)
- **Path:** `id`, `username`
- **Returns:** `data` = `ListString`

### `GET /api/v1/applications/{id}/users`

**Get assigned users for application.**

- **Permission:** `applications:edit:all`
- **Path:** `id`
- **Returns:** `data` = `ListAssignedUserDto`

### `POST /api/v1/applications/{id}/users`

**Assign a user to application.**

- **Permission:** `applications:edit:all`
- **Path:** `id`
- **Body:** JSON — `AssignUserRequest`
- **Returns:** `data` = `AssignedUserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` | yes |  |
| `accessLevel` | `string` | yes |  |

### `DELETE /api/v1/applications/{id}/users/{userId}`

**Remove assigned user from application.**

- **Permission:** `applications:edit:all`
- **Path:** `id`, `userId`
- **Returns:** `data` = `Void`

### `PUT /api/v1/applications/{id}/users/{userId}`

**Update assigned user access level.**

- **Permission:** `applications:edit:all`
- **Path:** `id`, `userId`
- **Body:** JSON — `UpdateAssignedUserRequest`
- **Returns:** `data` = `AssignedUserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `accessLevel` | `string` | yes |  |

## Application Connections

### `GET /api/v1/application-connections`

**Get all application connections.**
Retrieve all application connections for dependency mapping and threat modeling.

- **Permission:** `applications:read:all`
- **Returns:** `data` = `ListApplicationConnectionDto`

### `POST /api/v1/application-connections`

**Create a new application connection.**
Create a new connection between two applications.

- **Permission:** `applications:create:all`
- **Body:** JSON — `CreateApplicationConnectionRequest`
- **Returns:** `data` = `ApplicationConnectionDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `sourceApplicationId` | `string` | yes |  |
| `targetApplicationId` | `string` | yes |  |
| `type` | `DEPENDS_ON|USES_API|CONSUMES_DATA|AUTHENTICATES_WITH|SHARES_INFRASTRUCTURE|INTEGRATES_WITH` | yes |  |
| `description` | `string` |  |  |
| `critical` | `boolean` |  |  |
| `dataSensitivity` | `string` |  |  |

### `GET /api/v1/application-connections/application/{applicationId}/all`

**Get all connections for application.**
Retrieve all connections (both incoming and outgoing) for a specific application.

- **Permission:** `applications:read:all`
- **Path:** `applicationId`
- **Returns:** `data` = `ListApplicationConnectionDto`

### `GET /api/v1/application-connections/application/{applicationId}/incoming`

**Get incoming connections.**
Retrieve all incoming connections (dependents) for a specific application.

- **Permission:** `applications:read:all`
- **Path:** `applicationId`
- **Returns:** `data` = `ListApplicationConnectionDto`

### `GET /api/v1/application-connections/application/{applicationId}/outgoing`

**Get outgoing connections.**
Retrieve all outgoing connections (dependencies) for a specific application.

- **Permission:** `applications:read:all`
- **Path:** `applicationId`
- **Returns:** `data` = `ListApplicationConnectionDto`

### `DELETE /api/v1/application-connections/{id}`

**Delete a connection.**
Delete an application connection.

- **Permission:** `applications:delete:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/application-connections/{id}`

**Get connection by ID.**
Retrieve a specific application connection by its ID.

- **Permission:** `applications:read:all`
- **Path:** `id`
- **Returns:** `data` = `ApplicationConnectionDto`

### `PUT /api/v1/application-connections/{id}`

**Update an existing connection.**
Update an existing application connection.

- **Permission:** `applications:edit:all`
- **Path:** `id`
- **Body:** JSON — `UpdateApplicationConnectionRequest`
- **Returns:** `data` = `ApplicationConnectionDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `type` | `DEPENDS_ON|USES_API|CONSUMES_DATA|AUTHENTICATES_WITH|SHARES_INFRASTRUCTURE|INTEGRATES_WITH` | yes |  |
| `description` | `string` |  |  |
| `critical` | `boolean` |  |  |
| `dataSensitivity` | `string` |  |  |

## Client Images

### `GET /api/v1/organizations/{organizationId}/images`

**List an organization's images.**
Returns one entry per filled slot, with its metadata. The bytes are served separately, from the /content route.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Path:** `organizationId`
- **Returns:** `data` = `ListClientImageDto`

### `POST /api/v1/organizations/{organizationId}/images`

**Upload an organization image.**
Stores the file in the named slot (logo, cover, signature…), replacing whatever was there. PNG, JPEG, GIF, WebP or SVG, up to 5 MB.

- **Permission:** `organizations:edit:all`
- **Path:** `organizationId`
- **Query:** `name`
- **Body:** multipart/form-data
- **Returns:** `data` = `ClientImageDto`
- **Rule:** Multipart: `file` plus `name` (the slot, e.g. `logo`). Use PNG or JPEG — the report engine cannot rasterise SVG, so an SVG logo renders as an empty box.

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

### `DELETE /api/v1/organizations/{organizationId}/images/{name}`

**Delete an organization image.**
Empties the slot and removes the stored file.

- **Permission:** `organizations:edit:all`
- **Path:** `organizationId`, `name`
- **Returns:** `data` = `Void`

### `GET /api/v1/organizations/{organizationId}/images/{name}/content`

**Serve an organization image's bytes.**
Streams the stored file. Requires an authenticated caller — the interface fetches this as a blob rather than pointing an <img> tag at it.

- **Permission:** `organizations:read:all`, `organizations:read:owned`, `organizations:read:org` (any of)
- **Path:** `organizationId`, `name`

## Application ID Configuration

### `GET /api/v1/admin/application-id-config`

**Get application ID configuration.**
Retrieve the current application ID generation settings (prefix, padding, next sequence value).

- **Permission:** any signed-in user
- **Returns:** `data` = `ApplicationIdConfigDto`

### `PUT /api/v1/admin/application-id-config`

**Update application ID configuration.**
Update the application ID generation settings.

- **Permission:** any signed-in user
- **Body:** JSON — `ApplicationIdConfigUpdateRequest`
- **Returns:** `data` = `ApplicationIdConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `prefix` | `string` |  |  |
| `nextNumber` | `integer(int32)` |  |  |
| `enabled` | `boolean` |  |  |

### `GET /api/v1/admin/application-id-config/next`

**Generate next application ID.**
Generates and consumes the next application ID in the sequence.

- **Permission:** any signed-in user
- **Returns:** `data` = `String`

### `GET /api/v1/admin/application-id-config/preview`

**Preview upcoming application IDs.**
Returns the next IDs in the sequence without consuming them.

- **Permission:** any signed-in user
- **Query:** `count`
- **Returns:** `data` = `ListString`

## Campaigns

### `GET /api/v1/campaigns`

**Get all campaigns.**
Retrieves campaigns with pagination and optional case-insensitive name search

- **Permission:** `campaigns:read:all`
- **Query:** `page`, `size`, `sort`, `search`
- **Returns:** `data` = `ListCampaignDto`

### `POST /api/v1/campaigns`

**Create campaign.**

- **Permission:** `campaigns:create:all`
- **Body:** JSON — `CreateCampaignRequest`
- **Returns:** `data` = `CampaignDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |

### `GET /api/v1/campaigns/all`

**Get all campaigns (unpaged).**
Retrieves every campaign, for dropdown selectors

- **Permission:** `campaigns:read:all`
- **Returns:** `data` = `ListCampaignDto`

### `DELETE /api/v1/campaigns/{id}`

**Delete campaign.**
Deletes a campaign. Rejected while any assessment references it.

- **Permission:** `campaigns:delete:all`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/campaigns/{id}`

**Get campaign by ID.**

- **Permission:** `campaigns:read:all`
- **Path:** `id`
- **Returns:** `data` = `CampaignDto`

### `PUT /api/v1/campaigns/{id}`

**Update campaign.**
Renames a campaign and/or toggles its default flag (only one campaign is default at a time)

- **Permission:** `campaigns:edit:all`
- **Path:** `id`
- **Body:** JSON — `UpdateCampaignRequest`
- **Returns:** `data` = `CampaignDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `isDefault` | `boolean` |  |  |
