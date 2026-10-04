# In-app notifications and email

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Notifications](#notifications), [Notification Preferences](#notification-preferences), [Email Configuration](#email-configuration), [Email Notification Settings](#email-notification-settings), [Email Unsubscribe](#email-unsubscribe)

## Notifications

### `DELETE /api/v1/notifications`

**Delete all notifications for the current user.**

- **Permission:** any signed-in user
- **Returns:** `data` = `Void`

### `GET /api/v1/notifications`

**List all notifications for the current user.**

- **Permission:** any signed-in user
- **Returns:** `data` = `ListNotificationDto`

### `DELETE /api/v1/notifications/mentions`

**Delete mentions and thread replies for the current user.**
Clears the whole feed, or one section of it: targetType may be APPLICATION, VULNERABILITY, NOTEBOOK, or NONE for rows with no target.

- **Permission:** any signed-in user
- **Query:** `targetType`
- **Returns:** `data` = `Void`

### `GET /api/v1/notifications/mentions`

**List mentions and thread replies for the current user.**

- **Permission:** any signed-in user
- **Returns:** `data` = `ListNotificationDto`

### `GET /api/v1/notifications/mentions/unread-count`

**Get the count of unread mentions and thread replies.**

- **Permission:** any signed-in user
- **Returns:** `data` = `Long`

### `PATCH /api/v1/notifications/read-all`

**Mark all notifications as read.**

- **Permission:** any signed-in user
- **Returns:** `data` = `Void`

### `GET /api/v1/notifications/stream`

**Subscribe to real-time notification events via SSE.**

- **Permission:** any signed-in user
- **Returns:** `data` = `SseEmitter`

### `GET /api/v1/notifications/unread-count`

**Get the count of unread notifications.**

- **Permission:** any signed-in user
- **Returns:** `data` = `Long`

### `DELETE /api/v1/notifications/{id}`

**Delete a notification.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `Void`

### `PATCH /api/v1/notifications/{id}/read`

**Mark a single notification as read.**

- **Permission:** any signed-in user
- **Path:** `id`
- **Returns:** `data` = `NotificationDto`

## Notification Preferences

### `GET /api/v1/users/me/notification-preferences`

**Get the current user's notification preferences.**

- **Permission:** any signed-in user
- **Returns:** `data` = `ListNotificationPreferenceDto`

### `PUT /api/v1/users/me/notification-preferences`

**Update the current user's notification preferences.**

- **Permission:** any signed-in user
- **Body:** JSON — `UpdateNotificationPreferencesRequest`
- **Returns:** `data` = `ListNotificationPreferenceDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `preferences` | `Item[]` |  |  |

## Email Configuration

### `GET /api/v1/admin/email-config`

**Get SMTP email configuration.**

- **Permission:** any signed-in user
- **Returns:** `data` = `EmailConfigDto`

### `PUT /api/v1/admin/email-config`

**Update SMTP email configuration.**

- **Permission:** any signed-in user
- **Body:** JSON — `UpdateEmailConfigRequest`
- **Returns:** `data` = `EmailConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `enabled` | `boolean` |  |  |
| `provider` | `string` |  |  |
| `host` | `string` |  |  |
| `port` | `integer(int32)` |  |  |
| `username` | `string` |  |  |
| `password` | `string` |  |  |
| `fromName` | `string` |  |  |
| `fromEmail` | `string` |  |  |
| `security` | `string` |  |  |
| `authEnabled` | `boolean` |  |  |
| `logoBase64` | `string` |  |  |
| `logoMimeType` | `string` |  |  |

### `POST /api/v1/admin/email-config/test`

**Test SMTP connection and optionally send a test email.**

- **Permission:** any signed-in user
- **Body:** JSON — `TestEmailRequest`
- **Returns:** `data` = `TestSsoResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `to` | `string` |  |  |

## Email Notification Settings

### `GET /api/v1/admin/email-notification-config`

**Get which events email which audiences.**

- **Permission:** any signed-in user
- **Returns:** `data` = `EmailNotificationConfigDto`

### `PUT /api/v1/admin/email-notification-config`

**Update which events email which audiences.**

- **Permission:** any signed-in user
- **Body:** JSON — `UpdateEmailNotificationConfigRequest`
- **Returns:** `data` = `EmailNotificationConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `enabled` | `boolean` |  |  |
| `pastDueRepeatCount` | `integer(int32)` |  |  |
| `pastDueRepeatIntervalDays` | `integer(int32)` |  |  |
| `events` | `EventUpdate[]` |  |  |

## Email Unsubscribe

### `POST /api/v1/email/unsubscribe`

**Remove the holder of this token from the conversation.**

- **Permission:** none — public, no token needed
- **Body:** JSON — `UnsubscribeRequest`
- **Returns:** `data` = `Result`

| Field | Type | Required | Notes |
|---|---|---|---|
| `token` | `string` |  |  |
