# Management dashboards and the audit log

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [Manager Dashboard](#manager-dashboard), [Audit Logs](#audit-logs)

## Manager Dashboard

### `GET /api/v1/manager-dashboard/assessments`

**Filtered assessments (paginated).**
The assessments tab: filtered assessment rows annotated with assessor team names

- **Permission:** `manager_dashboard:read:all`
- **Query:** `params` (required), `page`, `size`, `sort`
- **Returns:** `data` = `ListManagerDashboardAssessmentDto`

### `GET /api/v1/manager-dashboard/export/assessments.csv`

**Export filtered assessments to CSV.**

- **Permission:** `manager_dashboard:read:all`
- **Query:** `params` (required)

### `GET /api/v1/manager-dashboard/export/vulnerabilities.csv`

**Export filtered cross-assessment vulnerabilities to CSV.**

- **Permission:** `manager_dashboard:read:all`
- **Query:** `params` (required)

### `GET /api/v1/manager-dashboard/stats`

**Filtered breakdown statistics.**
Severity, status, and completed-by-assessor breakdowns over the filtered assessment set

- **Permission:** `manager_dashboard:read:all`
- **Query:** `params` (required)
- **Returns:** `data` = `ManagerDashboardStatsDto`

### `GET /api/v1/manager-dashboard/summary`

**Global stats-card counts.**
Completed assessments and opened vulnerabilities per rolling period (week/month/year/all-time). Unaffected by filters.

- **Permission:** `manager_dashboard:read:all`
- **Returns:** `data` = `ManagerDashboardSummaryDto`

### `GET /api/v1/manager-dashboard/vulnerabilities`

**Filtered cross-assessment vulnerabilities (paginated).**
The vulnerabilities tab: every opened vulnerability (within the date range) across the filtered assessment set

- **Permission:** `manager_dashboard:read:all`
- **Query:** `params` (required), `page`, `size`, `sort`
- **Returns:** `data` = `ListManagerDashboardVulnerabilityDto`

### `GET /api/v1/manager-dashboard/vulnerabilities/{id}`

**Full vulnerability detail.**
One vulnerability with its parent assessment, for the dashboard's detail panel

- **Permission:** `manager_dashboard:read:all`
- **Path:** `id`
- **Returns:** `data` = `ManagerDashboardVulnerabilityDetailDto`

## Audit Logs

### `GET /api/v1/admin/logs/retests`

**List retest completions.**
Retests verified in the given window — what was retested, the verdict, and who signed off. `from` and `to` are ISO dates (inclusive); they default to the last 7 days, which is the "what did we complete this week" case. `result` narrows to PASS or FAIL. Cancelled retests are not completions and never appear.

- **Permission:** `audit:logs:read`
- **Query:** `page`, `size`, `sort`, `from`, `to`, `result`
- **Returns:** `data` = `ListRetestCompletionLogDto`

### `GET /api/v1/admin/logs/retests/summary`

**Retest pass/fail totals.**
How many retests passed and failed in the given window — the same window and defaults as the retest completion log, counted in the database rather than from a page.

- **Permission:** `audit:logs:read`
- **Query:** `from`, `to`
- **Returns:** `data` = `RetestActivitySummaryDto`
