# API reference

Generated from Faction's OpenAPI spec by `scripts/build_reference.py` — 317 operations. Do not edit by hand; change the generator and rerun it.

Every response is wrapped: success is `{success, message, data, pagination, timestamp}`, an error is `{timestamp, status, error, message, path}`. **Returns** below names the type of `data`. Every route takes `Authorization: Bearer <API key or JWT>` unless noted.

**Rule** lines are server behaviour the schemas do not declare. Read them — each one is a request that otherwise fails.

| File | Covers | Operations |
|---|---|---|
| [`engagements.md`](engagements.md) | Assessments, findings and the work around them | 108 |
| [`clients-and-targets.md`](clients-and-targets.md) | Clients (organizations), targets (applications) and campaigns | 54 |
| [`reporting.md`](reporting.md) | Report generation, templates and the content libraries they draw on | 51 |
| [`people-and-access.md`](people-and-access.md) | Sign-in, API keys, users, teams, roles and permissions | 42 |
| [`configuration.md`](configuration.md) | Assessment types, workflows and installation settings | 17 |
| [`notifications.md`](notifications.md) | In-app notifications and email | 18 |
| [`ai.md`](ai.md) | AI provider configuration, prompt templates and AI actions | 18 |
| [`dashboards-and-audit.md`](dashboards-and-audit.md) | Management dashboards and the audit log | 9 |
