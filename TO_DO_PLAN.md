# TO&nbsp;DO — Enable Report Sections without losing findings

**Audience:** an AI agent or developer picking this up cold. Read §1–§4 before writing code;
they contain the one fact that changes the whole shape of the task, and the failure mode that
makes a naive implementation silently delete findings from delivered reports.

**Repository:** `OWASP-Faction-2` (Apache 2.0), fork tracking
`factionsecurity/OWASP-Faction-2` as `upstream`.

**Line numbers** in this document were accurate at the time of writing. Verify by grepping for
the quoted code, not by trusting the number.

---

## 1. The task in one paragraph

The Report Designer's **Sections** tab shows "Not included in this edition" and refuses to
create sections. We need report sections working in this fork, because
`2._iSec_MAPT_Template_Faction.docx` files its findings into three of them
(`Android Application`, `iOS Application`, `API`) and will not render findings without them.
See `REPORT_TEMPLATE_UPDATES.md` §A.1.

---

## 2. The key discovery — read this before planning any code

**The report-sections feature is already fully implemented, end to end.** Nothing needs to be
written. It is switched off by an edition gate.

Evidence:

| Layer | State | Where |
|---|---|---|
| DOCX rendering per section | **Complete** | `DocxUtils.java:114-201` — `sectionVariable`, `getFilteredVulns(section)`, `sectionTag`, `trimSectionBlocks` |
| Per-section table + block expansion | **Complete** | `DocxUtils.java:716-721` — loops `Default` then every named section |
| Data model | **Complete** | `Assessment.sections` (`Assessment.java:119`), `Vulnerability.section` (`Vulnerability.java:169`) |
| Section CRUD API | **Complete** | `ReportTemplateService` create/update paths |
| Report Designer UI | **Complete** | `frontend/src/pages/ReportDesigner.tsx:467-479, 639-662, 949-1015` |
| Assessment section tabs | **Complete** | `frontend/src/pages/AssessmentDetail.tsx`, `AssessmentVulnerabilitySection.tsx` |
| **The gate** | **Closed** | `CommunityEditionPolicy.java` — `enabled()` returns `false` unconditionally |

So this is **not** a feature-implementation task. It is an *un-gating* task plus a set of
**safety guards that do not exist yet** (§4). Anyone who starts writing a sections feature from
scratch has misread the problem and will duplicate working code.

### How the edition system actually works

Read `EditionPolicy.java`'s class javadoc — it is accurate and worth reading in full. Summary:

- `EditionPolicy` is the single interface every gate calls. Callers never branch on `Edition`.
- `CommunityEditionPolicy` is the **only** implementation in this open-source build, and its
  `enabled(Feature)` is a hardcoded `return false;`.
- There is **no licence key and no runtime switch.** The paid build adds a `@Primary` bean
  that supersedes `CommunityEditionPolicy` purely by being on the classpath. The paid code is
  not in this repository at all.
- `application.yml:108` defines `faction.edition: ${FACTION_EDITION:enterprise}`. **Nothing in
  this repository reads it.** It is vestigial here — only the absent overlay consumes it.
  Setting `FACTION_EDITION=enterprise` does nothing. Do not waste time on it.

Because the licence is Apache 2.0 and this is our own fork, changing the policy is entirely
legitimate. There is no DRM to defeat — just a bean returning `false`.

---

## 3. Where the sections gate is enforced

Only two places gate `REPORT_SECTIONS`. Both matter, for different reasons.

### 3.1 Creation gate — `ReportTemplateService.java:624`

```java
private void requireSectionsAllowed(List<String> sections) {
    if (sections != null && sections.stream().anyMatch(sec -> sec != null && !sec.isBlank())) {
        editionPolicy.require(Feature.REPORT_SECTIONS);   // throws FeatureNotLicensedException -> HTTP 402
    }
}
```

This is what makes the Sections tab refuse. Clearing sections is deliberately always allowed.

### 3.2 Render gate — `DocxReportGenerationService.java:435`

```java
.sections(editionPolicy.enabled(Feature.REPORT_SECTIONS) && assessment.getSections() != null
        ? new ArrayList<>(assessment.getSections())
        : List.of())
```

**This one is dangerous and is the reason §4 exists.** When the gate is off, `sections` becomes
an empty list. `DocxUtils` then treats *every* finding as belonging to the implicit `Default`
section. For a template like ours that has no `Default` block, that means **every finding
disappears from the report, with no error**.

So the gate is not only "can you create sections" — flipping it off later silently empties
reports that used to work.

### 3.3 Frontend

`ReportDesigner.tsx:949` wraps the section editor in `<PaidFeature feature="report_sections">`.
`EditionContext.tsx` fetches `/api/v1/edition` and is **deliberately optimistic** — if the fetch
fails, every feature reads as available, because the backend is the real gate. Once the backend
reports the feature enabled, the UI unlocks with no frontend change required.

---

## 4. Why naive enabling loses findings — READ BEFORE CODING

The user's requirement was "without making another finding fail." These are the concrete ways a
finding vanishes from a delivered report. **None of them produce an error, a log line, or any
visible symptom.** The report simply renders without that finding.

The root cause is one line, `DocxUtils.java:808`:

```java
int begin = getIndex(mlp.getMainDocumentPart(), sectionTag("fiBegin", section));
int end   = getIndex(mlp.getMainDocumentPart(), sectionTag("fiEnd", section));
if (begin == -1 || end == -1) return;      // <-- section has no block: renders NOTHING, silently
```

Combined with `getFilteredVulns(section)` at `DocxUtils.java:153`, where `Default` absorbs
everything unfiled **and everything filed under a section the assessment does not have**:

```java
if (isDefaultSection(section)) {
    return vulns.stream()
            .filter(v -> isDefaultSection(v.getSection()) || !sectionExists(v.getSection()))
            .collect(Collectors.toList());
}
```

The comment there says such a finding is "still reported, not silently dropped" — that is true
only if the template has a `Default` block. Ours does not. For our template, Default is a
black hole.

### FM1 — Finding filed under a section the assessment lacks

Falls into `Default` → no `${fiBegin}` for Default in our template → gone.

### FM2 — `Assessment.sections` is a snapshot, not a live reference

`Assessment.java:115-119`:

```java
/** Snapshot of the template's sections at creation time. */
private List<String> sections = new ArrayList<>();
```

Adding a section to the template does **not** add it to assessments that already exist. A
finding filed under the new section on an older assessment hits FM1.

### FM3 — Section rename or delete orphans findings

`Vulnerability.section` stores the section **name as a string**, not an ID. Renaming
`API` to `API Findings` on the template orphans every finding referencing `API` → FM1.
There is no cascade and no referential integrity.

### FM4 — No validation on write

`VulnerabilityService.java:444-445`:

```java
if (request.getSection() != null)
    vuln.setSection(request.getSection().isBlank() ? null : request.getSection());
```

Any string is accepted. A typo, a stale client, or an API caller writes `"Andriod Application"`
and the finding is orphaned at write time with no complaint.

### FM5 — Section names are matched case-sensitively

`sectionVariable()` (`DocxUtils.java:129`) only collapses whitespace to underscores:

```java
return sectionName == null ? "" : sectionName.trim().replaceAll("\\s+", "_");
```

Matching is then exact string equality. `iOS Application` and `IOS Application` are different
sections. **Our template contains `${fiBegin iOS_Application}`, so the section must be named
exactly `iOS Application`** — capital I, capital O lowercase, capital A. Getting this wrong
puts every iOS finding into FM1.

### FM6 — Turning the gate back off

Per §3.2, disabling the feature after sections are in use empties the findings from every
report built on a sectioned template. Any toggle must be treated as a breaking change.

---

## 5. Design decision

### 5.1 How to enable — config-driven `@Primary` policy

**Chosen approach:** add a new `EditionPolicy` bean annotated `@Primary` that reads enabled
features from configuration, and **leave `CommunityEditionPolicy` untouched**.

```
backend/src/main/java/com/faction/clientportal/edition/SelfHostedEditionPolicy.java   (new)
```

Rationale:

- **It is the mechanism the codebase already documents.** `EditionPolicy`'s javadoc states the
  paid overlay "contributes a `@Primary` bean that supersedes it simply by being on the
  classpath." We are doing exactly that, in the open.
- **Zero merge conflicts with upstream.** We track `factionsecurity/OWASP-Faction-2`. Editing
  `CommunityEditionPolicy` would conflict on every upstream change to that file. A new file in
  a package upstream rarely touches will rebase cleanly.
- **Per-feature, not all-or-nothing.** Returning `true` from `CommunityEditionPolicy.enabled()`
  would also unlock `SSO`, `BRANDING`, `ENCRYPTED_PDF`, `EXTERNAL_OWNERS`, `CUSTOM_ROLES`,
  `INBOUND_EMAIL` and `AI_OBSERVABILITY`. Those have their own code paths of unknown
  completeness in the OSS build (`BootstrapService.java:57,62` seeds different roles when
  `CUSTOM_ROLES`/`EXTERNAL_OWNERS` are on — flipping those changes bootstrap behaviour). Enable
  exactly what we need.
- **Reversible and auditable.** The enabled set is configuration, visible in `application.yml`
  and overridable per environment.

**Rejected alternatives:**

| Option | Why not |
|---|---|
| Edit `CommunityEditionPolicy.enabled()` to return `true` | Unlocks seven unrelated features; guaranteed upstream merge conflicts |
| Make `CommunityEditionPolicy` read `faction.edition` | Contradicts its documented contract ("that override cannot unlock anything"); still conflicts on rebase |
| Delete the `require`/`enabled` calls at the two gate sites | Loses the seam entirely; conflicts; no way to turn back off |
| Fork the enterprise overlay | Does not exist in this repository |

### 5.2 How to prevent finding loss — fail loudly, never silently

Three layers, cheapest first. **Layer A is mandatory; it is the actual requirement.**

- **Layer A — preflight validation at generation time.** Before rendering, compute which
  findings would land in `Default`, and whether the template has a `Default` block. If findings
  would land somewhere the template cannot render, **fail the generation with a clear message
  naming the findings**, rather than producing a quietly incomplete report. A report missing
  findings is worse than no report: it gets delivered to a client.
- **Layer B — validation on write.** Reject a `section` value that is not one of the
  assessment's sections (`VulnerabilityService`), closing FM4 at the source.
- **Layer C — UI surfacing.** Show unfiled/orphaned findings in the assessment so they are
  visible before anyone generates.

---

## 6. Implementation plan

### Phase 1 — Enable the feature

**1.1** Create `backend/src/main/java/com/faction/clientportal/edition/SelfHostedEditionPolicy.java`:

- `@Service`, `@Primary`, implements `EditionPolicy`.
- Reads `faction.features.enabled` — a comma-separated list of `Feature` keys (the `key`
  field, e.g. `report_sections`, not the enum name). Parse leniently: trim, ignore case, ignore
  unknown keys with a `log.warn` rather than failing startup, so a typo does not take the
  application down.
- `edition()` → delegate to the wrapped `CommunityEditionPolicy` (`Edition.COMMUNITY`). We are
  not pretending to be Enterprise; we are a community build with a feature switched on. Keep
  this honest — `EditionStatusService` reports it to the UI and to support.
- `enabled(Feature)` → `true` if in the configured set, else delegate.
- `limit(Quota)` → delegate unchanged. **Do not** raise quotas here; out of scope.
- Inject `CommunityEditionPolicy` by concrete type to delegate to, avoiding a self-referential
  `EditionPolicy` injection cycle.

**1.2** `application.yml` — add under `faction:`:

```yaml
faction:
  features:
    enabled: ${FACTION_FEATURES_ENABLED:report_sections}
```

**1.3** Add `FACTION_FEATURES_ENABLED` to `.env.example` and `docker-compose.yml`
(`backend.environment`), documented as the fork's feature switch.

**Do not** change `application-test.yml` to enable it globally — tests that assert community
behaviour must keep seeing the feature off (see 4.1 below).

### Phase 2 — Layer A: preflight validation (the safety requirement)

**2.1** In `DocxUtils`, add a public inspection method — it already parses the document and
knows `sectionTag`:

```java
/** Section names the template has a findings block or table for, plus whether it has a Default one. */
public Set<String> renderableSections()
```

Implement by probing `getIndex(...)` for `${fiBegin …}` and `${vulnTable …}` for `Default` and
each of `data.getSections()`. **Must not mutate the document** — call it before `generateDocx`,
which is destructive (it removes marker paragraphs as it goes).

**2.2** In `DocxReportGenerationService`, before rendering:

- Compute `orphans` = findings whose `section` is non-blank and not in `assessment.getSections()`.
- Compute `unfiled` = findings with null/blank `section`.
- If (`orphans` ∪ `unfiled`) is non-empty **and** the template has no `Default` block → throw a
  new `ReportGenerationException` listing the offending finding titles and their section values.
- If a named section has findings but the template has no block for it → same failure, naming
  the section. This catches FM5 (the `iOS`/`IOS` mismatch) with an actionable message.

**2.3** Message wording matters — it is the whole point of this phase. It must name the
finding, the section value it carries, and what to do. For example:

> Report generation stopped: 3 findings would not appear in the report.
> "Insecure Data Storage" is filed under section "Andriod Application", which this assessment
> does not have (its sections are: Android Application, iOS Application, API).
> Either re-file these findings, or add a `${fiBegin}`/`${fiEnd}` block for the Default section
> to the template.

**2.4** Surface it as HTTP 422 with that message in the body, not a 500.

### Phase 3 — Layer B: validation on write

**3.1** In `VulnerabilityService` (create at `:178`, update at `:444`), validate the incoming
`section` against the parent assessment's `sections`. Reject with 400 and a message naming the
valid sections.

**3.2** Allow null/blank always (that is "unfiled", a legitimate state).

**3.3** **Grandfathering:** existing rows may already hold orphaned values. Validate only on
write, never on read, or the assessment page will 500 on historical data.

### Phase 4 — Layer C: UI surfacing (optional but recommended)

**4.1** In `AssessmentDetail.tsx`, show a warning banner when any finding is unfiled or
orphaned, linking to them. Follow `CLAUDE.md`: the routed page keeps its single `Page` wrapper
— do **not** wrap the nested section tabs.

**4.2** Do not block the user from saving an unfiled finding; unfiled is legitimate while
drafting. Warn, don't prevent.

---

## 7. Tests — required, per `CLAUDE.md`

> "Every backend feature addition or change requires a test. A feature is not complete until
> tests are written and passing." Run `mvn test` from `backend/`.

### 7.1 Existing tests that MUST keep passing

Two tests assert the gate is closed. **Do not delete them.** They must keep exercising
community behaviour, which means they must not pick up our new enabled-feature config.

| Test | Asserts |
|---|---|
| `ReportTemplateSectionsGateTest` (4 tests, `:75-100`) | Community cannot create/update a template with sections; enterprise can |
| `DocxReportGenerationServiceTest:205` | Stubs `enabled(REPORT_SECTIONS)`; verifies render-path behaviour |

**Verified safe:** both are annotated `@ExtendWith(MockitoExtension.class)` with `@Mock`
collaborators — neither loads a Spring context, so neither can pick up `SelfHostedEditionPolicy`.
Adding the new bean cannot affect them.

If you later add a gate test that *is* a `@SpringBootTest`, it will resolve the real `@Primary`
bean and community assertions will invert. The fix then is to leave `faction.features.enabled`
empty in `application-test.yml` so the test profile stays community by default — never to
weaken the assertion.

### 7.2 New tests to write

**`SelfHostedEditionPolicyTest`**
- configured with `report_sections` → `enabled(REPORT_SECTIONS)` is true
- `enabled(SSO)` and every other feature remain false
- empty/absent config → behaves exactly like `CommunityEditionPolicy`
- unknown key → warns, does not throw, other keys still parse
- `limit(Quota)` values unchanged from community
- `edition()` still reports `COMMUNITY`

**`ReportSectionPreflightTest`** — the core safety tests:
- finding with a section the assessment lacks + template without a Default block → generation
  fails, message names the finding and its section
- same, but template **has** a Default block → generation succeeds, finding renders in Default
- unfiled finding + no Default block → fails
- named section with findings but no block in the template → fails, names the section
- all findings correctly filed → succeeds, no warning
- **zero findings at all → succeeds** (an empty assessment must not be treated as an error)

**`VulnerabilityServiceSectionValidationTest`**
- section not in `assessment.sections` → 400
- valid section → accepted
- null/blank → accepted as unfiled
- existing orphaned row is readable and updatable in other fields without tripping validation

### 7.3 No database migration needed

Nothing in Phases 1–4 adds or alters a column. `Assessment.sections` and
`Vulnerability.section` already exist. If a later phase introduces section IDs (§9), that
**will** need a Flyway migration — follow `CLAUDE.md`: timestamped filename
(`date -u +%Y%m%d%H%M%S`), `DO $$ IF EXISTS(table) THEN … END $$` guard, `IF NOT EXISTS`.

Remember `CLAUDE.md`'s warning: `application-test.yml` uses `ddl-auto: create-drop`, so green
tests never prove a migration exists.

---

## 8. Manual verification

1. `mise run backend-up` and `mise run frontend-up` (see `DOCKER_QUICK_START.md`).
2. `curl -s localhost:8080/api/v1/edition | jq .features.report_sections` → `true`;
   every other feature → `false`.
3. Report Designer → Sections tab no longer shows "Not included in this edition".
4. Create the three sections **named exactly** `Android Application`, `iOS Application`, `API`
   (§4 FM5 — case matters).
5. Upload `2._iSec_MAPT_Template_Faction.docx`, create an assessment from that template.
6. Add findings to each section, plus **one deliberately orphaned finding** (set its section
   via API to a name that does not exist).
7. Generate → must **fail** with the Phase 2 message naming that finding. This is the test that
   the whole exercise was for.
8. Re-file the orphan, regenerate → succeeds; check the `${if-section}` removal by leaving one
   section empty.
9. Follow `REPORT_TEMPLATE_UPDATES.md` §G for the rest of the render checklist.

---

## 9. Explicitly out of scope

Do not expand into these without a new decision:

- **Section IDs instead of names** (fixes FM3 properly). Needs a schema migration and a
  backfill. Correct eventually; large.
- **Live section propagation to existing assessments** (FM2). Changing the snapshot semantics
  affects historical reports — a deliberate product decision, not a bug fix.
- **Enabling any other `Feature`.** Each has its own untested OSS code path;
  `CUSTOM_ROLES` and `EXTERNAL_OWNERS` alter `BootstrapService` seeding.
- **Raising `Quota` limits.** Unrelated to sections.
- **Upstreaming this.** The gate is upstream's product boundary; a PR removing it will be
  rejected. Keep it fork-local. (The `mise.toml` `maven = "3.9"` fix is a separate, genuinely
  upstreamable bug — do not bundle them.)

---

## 10. Suggested commit sequence

Per `CLAUDE.md`: `git commit -s` (DCO enforced in CI), and **never** add `Co-Authored-By`.
Branch off `main`; do not commit to `main` directly.

1. `feat(edition): add config-driven SelfHostedEditionPolicy` + its tests
2. `feat(report): fail generation when findings would not render` + preflight tests
3. `feat(vuln): validate section against the assessment's sections` + tests
4. `feat(ui): warn about unfiled and orphaned findings` (optional)

Keep 1 and 2 separate — 1 alone leaves the silent-loss hole open, and a reviewer should be able
to see that 2 closes it.
