# AI provider configuration, prompt templates and AI actions

Part of the generated API reference — see [`README.md`](README.md) for conventions.

**Areas:** [AI Configuration](#ai-configuration), [AI Prompt Templates](#ai-prompt-templates), [AI Actions](#ai-actions)

## AI Configuration

### `GET /api/v1/admin/ai-config`

**List AI providers.**
Every configured AI provider and which one is active. API keys are returned masked — the stored secret is never sent back.

- **Permission:** `ai:config:read`, `ai:config:write` (any of)
- **Returns:** `data` = `ListAiProviderConfigDto`

### `POST /api/v1/admin/ai-config`

**Add an AI provider.**
Registers a provider (model, endpoint, API key). Marking it active switches every AI feature over to it.

- **Permission:** `ai:config:write`
- **Body:** JSON — `SaveAiProviderConfigRequest`
- **Returns:** `data` = `AiProviderConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `providerType` | `OPENAI|ANTHROPIC|OPENROUTER|AZURE_OPENAI|OPENAI_COMPATIBLE` |  |  |
| `baseUrl` | `string` |  |  |
| `apiKey` | `string` |  |  |
| `apiVersion` | `string` |  |  |
| `models` | `string[]` |  |  |
| `defaultModel` | `string` |  |  |
| `enabled` | `boolean` |  |  |

### `GET /api/v1/admin/ai-config/anonymization`

**Get AI anonymization settings.**
Which identifying values (hosts, client names, and the like) are stripped from text before it leaves for the AI provider.

- **Permission:** `ai:config:read`, `ai:config:write` (any of)
- **Returns:** `data` = `AiAnonymizationConfigDto`

### `PUT /api/v1/admin/ai-config/anonymization`

**Update AI anonymization settings.**
Changes what gets redacted before text is sent to the AI provider.

- **Permission:** `ai:config:write`
- **Body:** JSON — `UpdateAiAnonymizationConfigRequest`
- **Returns:** `data` = `AiAnonymizationConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `enabled` | `boolean` |  |  |
| `presidioUrl` | `string` |  |  |
| `scoreThreshold` | `number(double)` |  |  |

### `POST /api/v1/admin/ai-config/test`

**Test an AI provider.**
Sends a probe request to the supplied provider settings and reports whether the credentials and model answer. Nothing is saved.

- **Permission:** `ai:config:write`
- **Body:** JSON — `TestAiProviderRequest`
- **Returns:** `data` = `TestAiProviderResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `string` |  |  |
| `providerType` | `OPENAI|ANTHROPIC|OPENROUTER|AZURE_OPENAI|OPENAI_COMPATIBLE` |  |  |
| `baseUrl` | `string` |  |  |
| `apiKey` | `string` |  |  |
| `apiVersion` | `string` |  |  |

### `GET /api/v1/admin/ai-config/usage`

**Daily AI token usage.**
Tokens consumed per day, summed across every user, provider and model. `from` and `to` are ISO dates (inclusive) and default to the start of last month through today, which is what the usage chart draws. Days with no AI activity are omitted rather than returned as zeros. Usage is recorded independently of AI request logging, so these totals are complete even while logging is off.

- **Permission:** `ai:config:read`, `ai:config:write` (any of)
- **Query:** `from`, `to`
- **Returns:** `data` = `ListAiTokenUsageDayDto`

### `GET /api/v1/admin/ai-config/web-search`

**Get web search configuration.**
Whether AI prompts may search the web, and the provider and key backing it (key masked).

- **Permission:** `ai:config:read`, `ai:config:write` (any of)
- **Returns:** `data` = `WebSearchConfigDto`

### `PUT /api/v1/admin/ai-config/web-search`

**Update web search configuration.**
Enables or disables web search for AI prompts and sets its provider credentials.

- **Permission:** `ai:config:write`
- **Body:** JSON — `UpdateWebSearchConfigRequest`
- **Returns:** `data` = `WebSearchConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `enabled` | `boolean` |  |  |
| `allowInAskAi` | `boolean` |  |  |
| `provider` | `BRAVE|TAVILY|SERPER` |  |  |
| `apiKey` | `string` |  |  |

### `DELETE /api/v1/admin/ai-config/{id}`

**Delete an AI provider.**
Removes the provider and its stored key. Deleting the active provider leaves AI features unconfigured until another is activated.

- **Permission:** `ai:config:write`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `PUT /api/v1/admin/ai-config/{id}`

**Update an AI provider.**
Changes the provider's settings. Leave the API key blank to keep the stored one.

- **Permission:** `ai:config:write`
- **Path:** `id`
- **Body:** JSON — `SaveAiProviderConfigRequest`
- **Returns:** `data` = `AiProviderConfigDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `providerType` | `OPENAI|ANTHROPIC|OPENROUTER|AZURE_OPENAI|OPENAI_COMPATIBLE` |  |  |
| `baseUrl` | `string` |  |  |
| `apiKey` | `string` |  |  |
| `apiVersion` | `string` |  |  |
| `models` | `string[]` |  |  |
| `defaultModel` | `string` |  |  |
| `enabled` | `boolean` |  |  |

## AI Prompt Templates

### `GET /api/v1/admin/ai-config/prompts`

**List AI prompt templates.**
Every configured prompt, enabled or not — the admin view. Editors see only the enabled ones, via GET /api/v1/ai/prompts.

- **Permission:** `ai:config:read`, `ai:config:write` (any of)
- **Returns:** `data` = `ListAiPromptTemplateDto`

### `POST /api/v1/admin/ai-config/prompts`

**Create an AI prompt template.**
Adds a prompt to the editor AI menu for the scope it declares.

- **Permission:** `ai:config:write`
- **Body:** JSON — `SaveAiPromptTemplateRequest`
- **Returns:** `data` = `AiPromptTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |
| `scope` | `ASSESSMENT|VULNERABILITY` |  |  |
| `prompt` | `string` |  |  |
| `providerId` | `string` |  |  |
| `model` | `string` |  |  |
| `allowWebAccess` | `boolean` |  |  |
| `enabled` | `boolean` |  |  |

### `DELETE /api/v1/admin/ai-config/prompts/{id}`

**Delete an AI prompt template.**
Removes the prompt. Editors stop offering it immediately; past runs stay in the AI request log.

- **Permission:** `ai:config:write`
- **Path:** `id`
- **Returns:** `data` = `Void`

### `PUT /api/v1/admin/ai-config/prompts/{id}`

**Update an AI prompt template.**
Replaces the prompt's name, scope, instruction text, and enabled flag.

- **Permission:** `ai:config:write`
- **Path:** `id`
- **Body:** JSON — `SaveAiPromptTemplateRequest`
- **Returns:** `data` = `AiPromptTemplateDto`

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | `string` |  |  |
| `description` | `string` |  |  |
| `scope` | `ASSESSMENT|VULNERABILITY` |  |  |
| `prompt` | `string` |  |  |
| `providerId` | `string` |  |  |
| `model` | `string` |  |  |
| `allowWebAccess` | `boolean` |  |  |
| `enabled` | `boolean` |  |  |

## AI Actions

### `POST /api/v1/ai/ask`

**Ask AI a free-form question.**
Free-form instruction over the editor's current content — the "Ask AI" box. Same access gate and assessment scoping as running a saved prompt.

- **Permission:** any signed-in user
- **Body:** JSON — `AskAiRequest`
- **Returns:** `data` = `AiGenerationResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `assessmentId` | `string` |  |  |
| `vulnerabilityId` | `string` |  |  |
| `question` | `string` |  |  |
| `currentText` | `string` |  |  |

### `POST /api/v1/ai/execute-prompt`

**Run a saved AI prompt.**
Runs one of the admin-defined prompts against the supplied text and assessment context. Assessment access is re-checked per request, so a caller only ever gets AI output over data they can already read.

- **Permission:** any signed-in user
- **Body:** JSON — `ExecuteAiPromptRequest`
- **Returns:** `data` = `AiGenerationResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `promptId` | `string` |  |  |
| `assessmentId` | `string` |  |  |
| `vulnerabilityId` | `string` |  |  |
| `currentText` | `string` |  |  |

### `GET /api/v1/ai/prompts`

**List available AI prompts.**
The enabled admin-defined prompts for the given editor scope, in the order the editor's AI menu should show them. Disabled prompts are never returned.

- **Permission:** any signed-in user
- **Query:** `scope` (required)
- **Returns:** `data` = `ListAiPromptSummaryDto`

### `POST /api/v1/ai/suggest-title`

**Suggest a title.**
Proposes a short title for the supplied body text, used when creating vulnerabilities and notebook pages.

- **Permission:** any signed-in user
- **Body:** JSON — `SuggestAiTitleRequest`
- **Returns:** `data` = `AiGenerationResponse`

| Field | Type | Required | Notes |
|---|---|---|---|
| `assessmentId` | `string` |  |  |
| `vulnerabilityId` | `string` |  |  |
| `description` | `string` |  |  |
| `details` | `string` |  |  |
