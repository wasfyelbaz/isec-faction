#!/usr/bin/env python3
"""Regenerate references/api/*.md from Faction's OpenAPI spec.

The spec is the source of truth for *what* exists. This script turns it into documentation an
agent can actually read — grouped by domain, one operation per block, request fields with their
types and which are required — and layers on two things the spec does not carry:

  * the permission each endpoint demands, recovered from the backend source if you point at it;
  * rules the server enforces that its schemas do not declare (KNOWN_RULES below).

Standard library only, so it runs anywhere Python 3.8+ does.

    # from a running installation, refreshing the bundled spec as well
    python scripts/build_reference.py --spec http://localhost:8080/v3/api-docs --save-spec

    # from the bundled spec, with permissions read from a checkout of the backend
    python scripts/build_reference.py --source ../backend/src/main/java
"""
import argparse
import json
import pathlib
import re
import sys
import urllib.request

SKILL = pathlib.Path(__file__).resolve().parent.parent
OUT = SKILL / "references" / "api"

# Faction tags every operation with its controller's area. These group them into files a reader
# can hold in their head; an unknown tag (a future controller) lands in "other" rather than vanishing.
GROUPS = {
    "engagements": ("Assessments, findings and the work around them", [
        "Assessments", "Vulnerabilities", "Vulnerabilities (Global)", "Retests", "Peer Reviews",
        "Remediation", "Notebook", "Assessment Checklists", "Assessment Surveys", "Inline Images",
        "Assessment Workflow Config"]),
    "clients-and-targets": ("Clients (organizations), targets (applications) and campaigns", [
        "Organizations", "Sub-Organizations", "Applications", "Application Connections",
        "Client Images", "Application ID Configuration", "Campaigns"]),
    "reporting": ("Report generation, templates and the content libraries they draw on", [
        "Reports", "Report Templates", "Report Fonts", "Default Vulnerabilities",
        "Vulnerability Categories", "Content Templates", "Checklist Templates", "Survey Templates",
        "Terminology", "Entity Field Configs"]),
    "people-and-access": ("Sign-in, API keys, users, teams, roles and permissions", [
        "Authentication", "API Keys", "Users", "User Profile", "Teams", "Roles", "Permissions",
        "Password Policy"]),
    "configuration": ("Assessment types, workflows and installation settings", [
        "Assessment Types", "Workflows", "Region Config", "Edition", "Status"]),
    "notifications": ("In-app notifications and email", [
        "Notifications", "Notification Preferences", "Email Configuration",
        "Email Notification Settings", "Email Unsubscribe"]),
    "ai": ("AI provider configuration, prompt templates and AI actions", [
        "AI Configuration", "AI Prompt Templates", "AI Actions"]),
    "dashboards-and-audit": ("Management dashboards and the audit log", [
        "Manager Dashboard", "Audit Logs"]),
}

# Rules the server enforces that the schemas do not say. Each one was learned by being refused.
# Keyed by "METHOD path", exactly as the spec spells the path.
KNOWN_RULES = {
    "POST /api/v1/auth/login":
        "The token is returned at the top level (`response.token`), not inside `data` like every "
        "other response. It expires after 24 hours; prefer an API key for agents.",
    "GET /api/v1/auth/me":
        "Make this the first call: it returns the principal's effective authorities, i.e. exactly what "
        "this token or API key is allowed to do. Needs a token despite living under `/auth`. Not "
        "enveloped: `username`, `id`, `roles`, `authorities` are top-level fields.",
    "GET /api/v1/assessments":
        "`showCompleted` includes completed assessments by default, whatever its description says; "
        "pass `showCompleted=false` for open ones only. Tell open from closed by the boolean "
        "`completed`, not the workflow-defined `status` label. Each assessment carries "
        "`vulnerabilitySummary` with per-severity counts.",
    "POST /api/v1/api-keys":
        "The raw key is in `data.key` and is shown exactly once. `scope` is `READ_WRITE` or "
        "`READ_ONLY`.",
    "POST /api/v1/applications":
        "`organizationId` (the client) is required — the server refuses a target with no client, "
        "even though the schema does not mark it. Each entry in `urls` needs both `url` and `title`.",
    "POST /api/v1/assessments":
        "A target is required even though the schema does not mark it: send `applicationId` of an "
        "existing target. `initialFieldValues` is keyed by the report template's field **id** "
        "(from `GET /report-templates/{id}` → `userDefinedFields[].id`), not by `variableName`.",
    "POST /api/v1/assessments/{assessmentId}/vulnerabilities":
        "`severity` is the enum `CRITICAL|HIGH|MEDIUM|LOW|INFORMATIONAL`, whatever labels the UI "
        "shows. `fieldValues` is keyed by field id, like an assessment's `initialFieldValues`.",
    "POST /api/v1/report-templates/{id}/file":
        "Send as multipart/form-data. The `file` part must carry the content type "
        "`application/vnd.openxmlformats-officedocument.wordprocessingml.document` or the upload "
        "fails with 400. Never upload a template containing Word comments — they are copied into "
        "every report generated from it.",
    "POST /api/v1/organizations/{organizationId}/images":
        "Multipart: `file` plus `name` (the slot, e.g. `logo`). Use PNG or JPEG — the report engine "
        "cannot rasterise SVG, so an SVG logo renders as an empty box.",
    "POST /api/v1/reports/{assessmentId}/generate":
        "Asynchronous: returns at once. Poll `GET /reports/{assessmentId}/documents` until no "
        "document is `GENERATING`, then download each with `.../documents/{type}/content`. "
        "Refused with 409 on a completed assessment — it must be reopened first; do not reopen a "
        "delivered engagement without asking. Prefer this over the older synchronous "
        "`POST /assessments/{id}/report/generate`, which produces fewer documents.",
    "POST /api/v1/assessments/{id}/report/generate":
        "The older, synchronous generator. Prefer `POST /reports/{assessmentId}/generate`, which is "
        "what the UI uses and produces the DOCX, the PDF and the encrypted PDF.",
    "POST /api/v1/assessments/import/preview":
        "Multipart `file`. Writes nothing. Every row needs a `client` column naming an existing "
        "client; rows naming an unknown target are flagged as creating one.",
    "POST /api/v1/assessments/import":
        "All or nothing — if any row is invalid, nothing is imported. Run the preview first and show "
        "a human the rows that create new targets.",
}


def load_spec(src):
    if re.match(r"https?://", src):
        with urllib.request.urlopen(src, timeout=60) as r:
            return json.loads(r.read().decode())
    return json.loads(pathlib.Path(src).read_text())


# ── permissions, recovered from the controllers ──────────────────────────────────────────────

def norm(path):
    """`/api/v1/x/{id}` and `/api/v1/x/{xId:.+}` must compare equal."""
    return re.sub(r"\{[^}]+\}", "{}", path.rstrip("/")) or "/"


def load_permissions(source):
    root = pathlib.Path(source)
    perm_file = next(root.rglob("model/Permission.java"), None)
    if perm_file is None:
        sys.exit(f"Permission.java not found under {source}")
    constants = dict(re.findall(r'^\s+([A-Z][A-Z0-9_]+)\("([^"]+)"', perm_file.read_text(), re.M))

    mapping_rx = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^)]*)\))?')
    found = {}
    for f in root.rglob("controller/**/*.java"):
        text = f.read_text()
        base = ""
        cls = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"', text)
        if cls:
            base = cls.group(1)
        for m in mapping_rx.finditer(text):
            method = m.group(1).upper()
            args = m.group(2) or ""
            # consumes = "multipart/form-data" and friends are strings too; they are not the path
            args = re.sub(r'\b(consumes|produces|headers|params)\s*=\s*(\{[^}]*\}|"[^"]*")', "", args)
            paths = re.findall(r'"([^"]*)"', args) or [""]
            # the permission annotation, if any, sits between this mapping and the method body
            tail = text[m.end(): m.end() + 1500]
            stop = re.search(r'\bpublic\s', tail)
            window = tail[: stop.start()] if stop else tail
            req = re.search(r'@RequiresPermission\(\s*\{?([^)}]*)\}?\s*\)', window)
            perms = []
            if req:
                for c in re.findall(r'Permission\.([A-Z0-9_]+)', req.group(1)):
                    perms.append(constants.get(c, c))
            for p in paths:
                full = (base + p) if not p.startswith("/api") else p
                found[(method, norm(full))] = perms
    return found


def load_public_routes(source):
    """(method or None, regex) for every permitAll() matcher in SecurityConfig."""
    cfg = next(pathlib.Path(source).rglob("config/SecurityConfig.java"), None)
    if cfg is None:
        return []
    out = []
    for method, args in re.findall(
            r'requestMatchers\(\s*(?:HttpMethod\.(\w+)\s*,\s*)?([^)]*)\)\s*\.permitAll', cfg.read_text()):
        for pattern in re.findall(r'"([^"]+)"', args):
            rx = "^" + re.escape(pattern).replace(r"\*\*", ".*").replace(r"\*", "[^/]+") + "$"
            out.append((method or None, re.compile(rx)))
    return out


def is_public(method, path, op, public_routes):
    # An operation that declares its own security requirement needs a token even when the
    # security layer lets the route through — /auth/me is the case that proves it.
    if op.get("security"):
        return False
    return any((m is None or m == method) and rx.match(path) for m, rx in public_routes)


# ── rendering ────────────────────────────────────────────────────────────────────────────────

def ref_name(schema):
    return schema["$ref"].rsplit("/", 1)[-1] if schema and "$ref" in schema else None


def type_of(schema, schemas):
    if not schema:
        return "any"
    if "$ref" in schema:
        name = ref_name(schema)
        target = schemas.get(name, {})
        if "enum" in target:
            return "|".join(map(str, target["enum"]))
        return name
    if "enum" in schema:
        return "|".join(map(str, schema["enum"]))
    t = schema.get("type")
    if isinstance(t, list):
        t = "/".join(x for x in t if x != "null")
    if t == "array":
        return f"{type_of(schema.get('items'), schemas)}[]"
    if t == "object" and "additionalProperties" in schema:
        return f"map<string, {type_of(schema['additionalProperties'], schemas)}>"
    fmt = schema.get("format")
    if fmt == "binary":
        return "file"
    return f"{t}({fmt})" if fmt and t else (t or "object")


def one_line(text, limit=160):
    if not text:
        return ""
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def fields_table(schema, schemas):
    """The request body's fields, one level deep — enough to build a request without guessing."""
    if not schema:
        return []
    if "$ref" in schema:
        schema = schemas.get(ref_name(schema), {})
    props = schema.get("properties", {})
    if not props:
        return []
    required = set(schema.get("required", []))
    rows = ["| Field | Type | Required | Notes |", "|---|---|---|---|"]
    for name, p in props.items():
        if p.get("readOnly"):
            continue
        rows.append(f"| `{name}` | `{type_of(p, schemas)}` | {'yes' if name in required else ''} | "
                    f"{one_line(p.get('description'), 110).replace('|', '/')} |")
    return rows


def render_op(method, path, op, schemas, perms, public=False):
    lines = [f"### `{method} {path}`", ""]
    summary = op.get("summary") or ""
    if summary:
        lines.append(f"**{summary}.**" if not summary.endswith(".") else f"**{summary}**")
    desc = one_line(op.get("description"), 400)
    if desc and desc != summary:
        lines.append(desc)
    lines.append("")

    if public:
        lines.append("- **Permission:** none — public, no token needed")
    elif perms is not None:
        lines.append(f"- **Permission:** {', '.join(f'`{p}`' for p in perms) + (' (any of)' if len(perms) > 1 else '') if perms else 'any signed-in user'}")

    params = op.get("parameters", [])
    path_params = [p for p in params if p.get("in") == "path"]
    query_params = [p for p in params if p.get("in") == "query"]
    if path_params:
        lines.append("- **Path:** " + ", ".join(f"`{p['name']}`" for p in path_params))
    if query_params:
        lines.append("- **Query:** " + ", ".join(
            f"`{p['name']}`" + (" (required)" if p.get("required") else "") for p in query_params))

    body = op.get("requestBody")
    table = []
    if body:
        content = body.get("content", {})
        ctype, media = next(iter(content.items()))
        schema = media.get("schema")
        resolved = schemas.get(ref_name(schema), schema) if schema else {}
        binary = any((v or {}).get("format") == "binary" for v in (resolved or {}).get("properties", {}).values())
        if binary or "multipart" in ctype:
            lines.append("- **Body:** multipart/form-data")
        else:
            name = ref_name(schema)
            lines.append(f"- **Body:** JSON" + (f" — `{name}`" if name else ""))
        table = fields_table(schema, schemas)

    resp = op.get("responses", {})
    ok = next((resp[c] for c in ("200", "201") if c in resp), None)
    if ok:
        media = next(iter(ok.get("content", {}).values()), {})
        name = ref_name(media.get("schema"))
        if name and name not in ("ApiResponse", "JsonApiResponse"):
            name = re.sub(r"^JsonApiResponse", "", name)
            lines.append(f"- **Returns:** `data` = `{name}`")

    rule = KNOWN_RULES.get(f"{method} {path}")
    if rule:
        lines.append(f"- **Rule:** {rule}")

    if table:
        lines += ["", *table]
    lines.append("")
    return lines


def build(spec, perms_by_route, public_routes=()):
    schemas = spec.get("components", {}).get("schemas", {})
    tag_to_group = {t: g for g, (_, tags) in GROUPS.items() for t in tags}
    buckets = {g: {} for g in list(GROUPS) + ["other"]}
    total = 0
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method not in ("get", "post", "put", "patch", "delete"):
                continue
            tag = (op.get("tags") or ["Other"])[0]
            group = tag_to_group.get(tag, "other")
            buckets[group].setdefault(tag, []).append((method.upper(), path, op))
            total += 1

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.md"):
        old.unlink()

    index = [
        "# API reference",
        "",
        f"Generated from Faction's OpenAPI spec by `scripts/build_reference.py` — {total} operations. "
        "Do not edit by hand; change the generator and rerun it.",
        "",
        "Every response is wrapped: success is `{success, message, data, pagination, timestamp}`, an "
        "error is `{timestamp, status, error, message, path}`. **Returns** below names the type of "
        "`data`. Every route takes `Authorization: Bearer <API key or JWT>` unless noted.",
        "",
        "**Rule** lines are server behaviour the schemas do not declare. Read them — each one is a "
        "request that otherwise fails.",
        "",
        "| File | Covers | Operations |",
        "|---|---|---|",
    ]
    for group, tags in buckets.items():
        if not tags:
            continue
        title = GROUPS[group][0] if group in GROUPS else "Areas not yet grouped"
        count = sum(len(v) for v in tags.values())
        index.append(f"| [`{group}.md`]({group}.md) | {title} | {count} |")
        body = [f"# {title}", "",
                "Part of the generated API reference — see [`README.md`](README.md) for conventions.", ""]
        order = GROUPS.get(group, (None, sorted(tags)))[1]
        body.append("**Areas:** " + ", ".join(f"[{t}](#{re.sub(r'[^a-z0-9 -]', '', t.lower()).replace(' ', '-')})"
                                               for t in order if t in tags))
        body.append("")
        for tag in order:
            if tag not in tags:
                continue
            body += [f"## {tag}", ""]
            for method, path, op in sorted(tags[tag], key=lambda x: (x[1], x[0])):
                perms = perms_by_route.get((method, norm(path))) if perms_by_route is not None else None
                body += render_op(method, path, op, schemas, perms,
                                  public=is_public(method, path, op, public_routes))
        (OUT / f"{group}.md").write_text("\n".join(body).rstrip() + "\n")
    index.append("")
    (OUT / "README.md").write_text("\n".join(index))
    return total


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--spec", default=str(SKILL / "references" / "openapi.json"),
                    help="path or URL of the OpenAPI JSON (default: the bundled copy)")
    ap.add_argument("--source", help="backend Java source root, to recover per-endpoint permissions")
    ap.add_argument("--save-spec", action="store_true",
                    help="also overwrite references/openapi.json with the spec that was read")
    a = ap.parse_args()

    spec = load_spec(a.spec)
    if a.save_spec:
        (SKILL / "references" / "openapi.json").write_text(json.dumps(spec, indent=1))
    perms = load_permissions(a.source) if a.source else None
    public = load_public_routes(a.source) if a.source else []
    total = build(spec, perms, public)
    matched = sum(1 for v in (perms or {}).values()) if perms else 0
    print(f"wrote {OUT.relative_to(SKILL)}/ — {total} operations"
          + (f", permissions recovered for {matched} controller routes" if perms else ""))


if __name__ == "__main__":
    main()
