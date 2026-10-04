# Sign-in, API keys, users, teams, roles and permissions

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Authentication](#authentication), [API Keys](#api-keys), [Users](#users), [User Profile](#user-profile), [Teams](#teams), [Roles](#roles), [Permissions](#permissions), [Password Policy](#password-policy)

## Authentication

### `POST /api/v1/auth/forgot-password`

**Request password reset.**
Sends a password reset link to the given email if an account exists. Always returns 200 to avoid revealing whether the email is registered.

- **Permission:** none — public, no token needed
- **Body:** JSON — `ForgotPasswordRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `email` | `string` | yes |  |

### `POST /api/v1/auth/login`

**User login.**
Authenticate user and return JWT token with authorities

- **Permission:** none — public, no token needed
- **Body:** JSON — `LoginRequest`
- **Returns:** `data` = `LoginResponse`
- **Rule:** The token is returned at the top level (`response.token`), not inside `data` like every other response. It expires after 24 hours; prefer an API key for agents.

| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | `string` | yes |  |
| `password` | `string` | yes |  |

### `POST /api/v1/auth/logout`

**Log out.**
Clears the media access cookie. The JWT itself is stateless, so the client must also discard its copy of the token.

- **Permission:** none — public, no token needed

### `GET /api/v1/auth/me`

**Get current principal.**
Returns the authenticated principal's username, effective authorities, and user id (omitted for system API-key principals).

- **Permission:** any signed-in user
- **Rule:** Make this the first call: it returns the principal's effective authorities, i.e. exactly what this token or API key is allowed to do. Needs a token despite living under `/auth`. Not enveloped: `username`, `id`, `roles`, `authorities` are top-level fields.

### `POST /api/v1/auth/reset-password`

**Reset password.**
Sets a new password using a reset token from the password reset email. Returns 400 if the token is invalid or expired.

- **Permission:** none — public, no token needed
- **Body:** JSON — `ResetPasswordRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `token` | `string` | yes |  |
| `newPassword` | `string` | yes |  |

## API Keys

### `GET /api/v1/api-keys`

**List your API keys.**
List the authenticated user's active (non-revoked) API keys, newest first. The secret is never included — only non-secret metadata and the display hint.

- **Permission:** any signed-in user
- **Returns:** `data` = `ApiKeyDto`

### `POST /api/v1/api-keys`

**Create your API key.**
Mint a new API key owned by the authenticated user. Scope is READ_WRITE (all of your live permissions — the default) or READ_ONLY (the read-only slice of them); either way the key's authorities are resolved fresh on every request, so they always track your current access. The plaintext key is returned exactly once in the response and is never stored — save it immediately. External (portal) users…

- **Permission:** any signed-in user
- **Body:** JSON — `CreateApiKeyRequest`
- **Returns:** `data` = `CreateApiKeyResponse`
- **Rule:** The raw key is in `data.key` and is shown exactly once. `scope` is `READ_WRITE` or `READ_ONLY`.

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `scope` | `READ_WRITE|READ_ONLY|CUSTOM` |  |  |

### `GET /api/v1/api-keys/system`

**List system API keys.**
List all active (non-revoked) system keys, newest first. The secret is never included — only non-secret metadata, assigned permissions, and the hint.

- **Permission:** any signed-in user
- **Returns:** `data` = `ApiKeyDto`

### `POST /api/v1/api-keys/system`

**Create a system API key.**
Mint an ownerless system (service-account) key with the given permissions. Restricted to super admins: a system key is an unbounded, ownerless credential that can carry any authority, so minting one is an administrative act. The plaintext key is returned exactly once and is never stored. A key created with no permissions authenticates but is authorized for nothing.

- **Permission:** any signed-in user
- **Body:** JSON — `CreateSystemApiKeyRequest`
- **Returns:** `data` = `CreateApiKeyResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `permissions` | `string[]` |  |  |

### `DELETE /api/v1/api-keys/system/{id}`

**Revoke a system API key.**
Revoke a system (service-account) key. Revocation is permanent and takes effect immediately.

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `Void`

### `PUT /api/v1/api-keys/system/{id}`

**Update a system API key.**
Rename and/or re-scope a system (service-account) key. Restricted to super admins, since re-scoping can grant any authority. Permissions are unrestricted; an empty list leaves the key inert. Does not change the secret.

- **Permission:** any signed-in user
- **Path:** `id`
- **Body:** JSON — `UpdateSystemApiKeyRequest`
- **Returns:** `data` = `ApiKeyDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `permissions` | `string[]` |  |  |

### `DELETE /api/v1/api-keys/{id}`

**Revoke your API key.**
Revoke one of the authenticated user's own API keys. Revocation is permanent and takes effect immediately.

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `Void`

## Users

### `GET /api/v1/users`

**Get all users.**
Retrieve all users with pagination and optional search. Super admins and users with 'users:read:all' can see all users. Users with 'users:read:team' can only see users in their teams.

- **Permission:** `users:read:all`, `users:read:team` (any of)
- **Query:** `page`, `size`, `sort`, `search`, `roleId`, `teamId`, `organizationId`, `type`

### `POST /api/v1/users`

**Create a new user.**
Create a new user with specified details. Super admins and users with 'users:create:all' can create any user. Users with 'users:create:team' can only create users for their teams.

- **Permission:** `users:create:all`, `users:create:team` (any of)
- **Body:** JSON — `CreateUserRequest`
- **Returns:** `data` = `UserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | `string` | yes |  |
| `email` | `string` | yes |  |
| `firstName` | `string` | yes |  |
| `lastName` | `string` | yes |  |
| `password` | `string` | yes |  |
| `loginOption` | `NATIVE|SAML2|OPENID` | yes |  |
| `roleIds` | `string[]` | yes |  |
| `teamIds` | `string[]` |  |  |
| `isInternal` | `boolean` | yes |  |
| `organizationIds` | `string[]` |  |  |
| `subOrganizationIds` | `string[]` |  |  |
| `organizationId` | `string` |  |  |
| `disabled` | `boolean` |  |  |

### `GET /api/v1/users/mentionable`

**Get @mention candidates.**
Users the caller may mention, optionally narrowed to the conversation being written to. External users see their own organization plus the remediation contact and existing thread participants.

- **Permission:** any signed-in user
- **Query:** `search`, `vulnerabilityId`, `applicationId`
- **Returns:** `data` = `ListMentionableUserDto`

### `DELETE /api/v1/users/{id}`

**Delete a user.**
Soft delete a user by setting deletedAt timestamp. Super admins and users with 'users:delete:all' can delete any user. Users with 'users:delete:team' can only delete users in their teams.

- **Permission:** `users:delete:all`, `users:delete:team` (any of)
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/users/{id}`

**Get user by ID.**
Retrieve a specific user by their ID. Super admins and users with 'users:read:all' can see any user. Users with 'users:read:team' can only see users in their teams.

- **Permission:** `users:read:all`, `users:read:team` (any of)
- **Path:** `id`
- **Returns:** `data` = `UserDto`

### `PUT /api/v1/users/{id}`

**Update an existing user.**
Update an existing user's details. Super admins and users with 'users:edit:all' can update any user. Users with 'users:edit:team' can only update users in their teams.

- **Permission:** `users:edit:all`, `users:edit:team` (any of)
- **Path:** `id`
- **Body:** JSON — `UpdateUserRequest`
- **Returns:** `data` = `UserDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | `string` | yes |  |
| `email` | `string` | yes |  |
| `firstName` | `string` | yes |  |
| `lastName` | `string` | yes |  |
| `password` | `string` |  |  |
| `loginOption` | `NATIVE|SAML2|OPENID` | yes |  |
| `roleIds` | `string[]` | yes |  |
| `teamIds` | `string[]` |  |  |
| `isInternal` | `boolean` | yes |  |
| `organizationIds` | `string[]` |  |  |
| `subOrganizationIds` | `string[]` |  |  |
| `organizationId` | `string` |  |  |
| `disabled` | `boolean` |  |  |

### `GET /api/v1/users/{id}/application-assignments`

**List the applications a user is assigned to (app-level owner access).**

- **Permission:** `applications:edit:all`
- **Path:** `id`
- **Returns:** `data` = `ListUserApplicationAssignmentDto`

### `PUT /api/v1/users/{id}/application-assignments`

**Replace the full set of a user's application assignments.**
Adds/updates the listed applications and removes the user from any others. An empty list clears app-level restriction, returning the user to organization-level access (if they have a home organization).

- **Permission:** `applications:edit:all`
- **Path:** `id`
- **Body:** JSON — `SyncUserApplicationAssignmentsRequest`
- **Returns:** `data` = `ListUserApplicationAssignmentDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `assignments` | `Assignment[]` |  |  |

### `POST /api/v1/users/{id}/password-reset`

**Send a password reset link to a user.**
Emails a reset link to the user, on an administrator's behalf. Unlike the public forgot-password endpoint, which must answer identically whatever happens so it cannot be used to discover which addresses have accounts, this one reports what actually happened: that the user has no email address, that they sign in through an identity provider, or that email is not configured.

- **Permission:** `users:edit:all`, `users:edit:team` (any of)
- **Path:** `id`
- **Returns:** `data` = `MapStringString`

## User Profile

### `GET /api/v1/profile-images/{imageId}`

**Serve a profile image.**
Streams the profile image bytes to any authenticated caller. 404 if not found.

- **Permission:** any signed-in user
- **Path:** `imageId`

### `GET /api/v1/users/avatars`

**Get avatar map.**
Returns avatar info (default-avatar seed and uploaded profile image id) for every active user, keyed by both user id and username. Used to resolve avatars consistently across discussion areas and the top bar.

- **Permission:** any signed-in user
- **Returns:** `data` = `MapStringAvatarInfo`

### `GET /api/v1/users/me`

**Get current user profile.**
Returns the profile of the currently authenticated user.

- **Permission:** any signed-in user
- **Returns:** `data` = `UserDto`

### `POST /api/v1/users/me/change-password`

**Change own password.**
Changes the current user's password after verifying the current one. Rejected for SSO-managed accounts.

- **Permission:** any signed-in user
- **Body:** JSON — `ChangePasswordRequest`
- **Returns:** `data` = `Void`

| Field | Type | Required | Notes |
|---|---|---|---|
| `currentPassword` | `string` | yes |  |
| `newPassword` | `string` | yes |  |

### `DELETE /api/v1/users/me/profile-image`

**Remove own profile image.**
Removes the current user's uploaded profile image, reverting to the default avatar.

- **Permission:** any signed-in user
- **Returns:** `data` = `Void`

### `POST /api/v1/users/me/profile-image`

**Upload own profile image.**
Uploads a profile image (PNG/JPEG/GIF/WebP, max 2 MB) for the current user, replacing any existing one. Returns the new profile image id.

- **Permission:** any signed-in user
- **Body:** multipart/form-data
- **Returns:** `data` = `MapStringString`

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | `file` | yes |  |

## Teams

### `GET /api/v1/teams`

**Get all teams.**
Retrieves all teams with pagination support and optional search by name or description (case-insensitive)

- **Permission:** any signed-in user
- **Query:** `page`, `size`, `sort`, `search`
- **Returns:** `data` = `ListTeamDto`

### `POST /api/v1/teams`

**Create team.**
Creates a new team

- **Permission:** any signed-in user
- **Body:** JSON — `CreateTeamRequest`
- **Returns:** `data` = `TeamDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |

### `DELETE /api/v1/teams/{id}`

**Delete team.**
Deletes a team and removes it from all users

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `Void`

### `GET /api/v1/teams/{id}`

**Get team by ID.**
Retrieves a specific team by its ID

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `TeamDto`

### `PUT /api/v1/teams/{id}`

**Update team.**
Updates an existing team

- **Permission:** any signed-in user
- **Path:** `id`
- **Body:** JSON — `UpdateTeamRequest`
- **Returns:** `data` = `TeamDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |

### `GET /api/v1/teams/{teamId}/users`

**Get users in team.**
Retrieves all users that are members of a specific team

- **Permission:** any signed-in user
- **Path:** `teamId`
- **Returns:** `data` = `ListUserDto`

### `DELETE /api/v1/teams/{teamId}/users/{userId}`

**Remove user from team.**
Removes a user from a team

- **Permission:** any signed-in user
- **Path:** `teamId`, `userId`
- **Returns:** `data` = `Void`

### `POST /api/v1/teams/{teamId}/users/{userId}`

**Add user to team.**
Adds a user to a team

- **Permission:** any signed-in user
- **Path:** `teamId`, `userId`
- **Returns:** `data` = `Void`

## Roles

### `GET /api/v1/roles`

**Get all roles.**
Retrieve all roles with their assigned permissions. Supports pagination and optional search by name or description (case-insensitive). Only accessible to Super Admins.

- **Permission:** `roles:read:all`
- **Query:** `page`, `size`, `sort`, `search`

### `POST /api/v1/roles`

**Create a new role.**
Create a new role with specified permissions. Only accessible to Super Admins.

- **Permission:** `roles:create:all`
- **Body:** JSON — `CreateRoleRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |
| `permissions` | `string[]` | yes |  |
| `externalRole` | `boolean` |  |  |

### `DELETE /api/v1/roles/{id}`

**Delete a role.**
Delete a role. Default roles (SuperAdmin and Pentester) cannot be deleted. Only accessible to Super Admins.

- **Permission:** `roles:delete:all`
- **Path:** `id`

### `PUT /api/v1/roles/{id}`

**Update an existing role.**
Update an existing role's name, description, or permissions. Only accessible to Super Admins.

- **Permission:** `roles:edit:all`
- **Path:** `id`
- **Body:** JSON — `UpdateRoleRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` | yes |  |
| `description` | `string` |  |  |
| `permissions` | `string[]` | yes |  |
| `externalRole` | `boolean` |  |  |

## Permissions

### `GET /api/v1/permissions`

**Get all permissions.**
Retrieves all available permissions organized by resource. Used for role management UI.

- **Permission:** `roles:read:all`
- **Returns:** `data` = `ListResourcePermissionsDto`

## Password Policy

### `GET /api/v1/config/password-policy`

**Get the password policy.**
Readable by any signed-in user, because every password field has to show the rules it is about to enforce. It describes requirements, not secrets.

- **Permission:** any signed-in user
- **Returns:** `data` = `PasswordPolicy`

### `PUT /api/v1/config/password-policy`

**Update the password policy.**
Replaces the installation's password and sign-in rules. A lockout duration of 0 means the account stays locked until an administrator re-enables it; any other value is a cooldown that lifts itself. A failed-attempt limit of 0 switches lockout off entirely.

- **Permission:** `config:write`
- **Body:** JSON — `PasswordPolicy`
- **Returns:** `data` = `PasswordPolicy`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `string` |  |  |
| `maxFailedLoginAttempts` | `integer(int32)` |  |  |
| `lockoutDurationMinutes` | `integer(int32)` |  |  |
| `minimumLength` | `integer(int32)` |  |  |
| `requireUppercase` | `boolean` |  |  |
| `requireLowercase` | `boolean` |  |  |
| `requireDigit` | `boolean` |  |  |
| `requireSymbol` | `boolean` |  |  |
