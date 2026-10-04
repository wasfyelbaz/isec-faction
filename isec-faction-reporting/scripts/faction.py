#!/usr/bin/env python3
"""faction.py — a small, dependency-free client for the iSec Faction API.

Any agent with a shell can drive the whole platform through this one file: it handles
authentication, the response envelope, pagination, multipart uploads, downloads, and the
asynchronous report generation that is easy to get wrong by hand.

Configuration (environment variables):
  FACTION_URL        base URL, default http://localhost:8080
  FACTION_TOKEN      an API key (sk_fac_...) or a JWT — preferred
  FACTION_USER /     used to sign in when FACTION_TOKEN is unset
  FACTION_PASSWORD

Commands:
  whoami                                  who this token is, and what it may do
  find  <words...>                        search the bundled OpenAPI spec for operations
  call  <METHOD> <PATH> [--data JSON|@file] [--query k=v ...] [--all-pages]
                                          (--all-pages merges every page into
                                          {success, data, count}; without it you get the
                                          API's own envelope, pagination included)
  fields <REPORT_TEMPLATE_ID>             a template's user-defined fields, with the ids
                                          that initialFieldValues / fieldValues are keyed by
  upload <PATH> --file F [--field k=v ...] [--file-field file] [--type MIME]
  download <PATH> --out FILE
  generate <ASSESSMENT_ID> [--out-dir DIR] [--types DOCX,PDF] [--name STEM] [--timeout SECONDS]

Output is JSON on stdout. Failures print the server's message on stderr and exit non-zero.
The token is never printed.
"""
import argparse
import json
import mimetypes
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

SKILL = pathlib.Path(__file__).resolve().parent.parent
SPEC = SKILL / "references" / "openapi.json"
DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
TYPES = {".docx": DOCX, ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
         ".csv": "text/csv", ".json": "application/json", ".pdf": "application/pdf"}


class FactionError(Exception):
    def __init__(self, status, message, body=None):
        super().__init__(f"HTTP {status}: {message}")
        self.status, self.message, self.body = status, message, body


class Client:
    def __init__(self, base=None, token=None):
        self.base = (base or os.environ.get("FACTION_URL") or "http://localhost:8080").rstrip("/")
        self.token = token or os.environ.get("FACTION_TOKEN")
        if not self.token and os.environ.get("FACTION_USER"):
            self.token = self._login(os.environ["FACTION_USER"], os.environ.get("FACTION_PASSWORD", ""))

    def _login(self, user, password):
        # The one response that is not enveloped: the token sits at the top level.
        resp = self.request("POST", "/api/v1/auth/login", data={"username": user, "password": password},
                            auth=False)
        token = resp.get("token")
        if not token:
            raise FactionError(401, "login returned no token")
        return token

    def _url(self, path, query=None):
        if not path.startswith("/"):
            path = "/" + path
        if not path.startswith("/api/") and not path.startswith("/v3/"):
            path = "/api/v1" + path          # let callers write /assessments for /api/v1/assessments
        url = self.base + path
        if query:
            url += ("&" if "?" in url else "?") + urllib.parse.urlencode(query, doseq=True)
        return url

    def raw(self, method, path, body=None, content_type=None, query=None, auth=True):
        req = urllib.request.Request(self._url(path, query), data=body, method=method.upper())
        if content_type:
            req.add_header("Content-Type", content_type)
        req.add_header("Accept", "application/json, */*")
        if auth:
            if not self.token:
                raise FactionError(401, "no credentials: set FACTION_TOKEN, or FACTION_USER and FACTION_PASSWORD")
            req.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            payload = e.read()
            message = payload.decode(errors="replace")[:500]
            try:
                parsed = json.loads(payload)
                message = parsed.get("message") or parsed.get("error") or message
            except ValueError:
                parsed = None
            raise FactionError(e.code, message, parsed) from None
        except urllib.error.URLError as e:
            raise FactionError(0, f"cannot reach {self.base}: {e.reason}") from None

    def request(self, method, path, data=None, query=None, auth=True):
        body = None if data is None else json.dumps(data).encode()
        _, _, payload = self.raw(method, path, body, "application/json" if body else None, query, auth)
        if not payload:
            return {}
        try:
            return json.loads(payload)
        except ValueError:
            return {"raw": payload.decode(errors="replace")}

    def all_pages(self, path, query=None, size=100):
        """Follow `pagination` to the end — one page is rarely everything."""
        query = dict(query or {})
        query.setdefault("size", size)
        page, items = 0, []
        while True:
            query["page"] = page
            resp = self.request("GET", path, query=query)
            data = resp.get("data")
            items.extend(data if isinstance(data, list) else ([data] if data else []))
            pg = resp.get("pagination") or {}
            if not pg or pg.get("last", True):
                return {"success": True, "data": items, "count": len(items)}
            page += 1

    def upload(self, path, file_path, fields=None, file_field="file", mime=None):
        f = pathlib.Path(file_path)
        mime = mime or TYPES.get(f.suffix.lower()) or mimetypes.guess_type(f.name)[0] or "application/octet-stream"
        boundary = "----faction" + uuid.uuid4().hex
        parts = []
        for k, v in (fields or {}).items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; '
                      f'filename="{f.name}"\r\nContent-Type: {mime}\r\n\r\n').encode())
        parts.append(f.read_bytes())
        parts.append(f"\r\n--{boundary}--\r\n".encode())
        _, _, payload = self.raw("POST", path, b"".join(parts), f"multipart/form-data; boundary={boundary}")
        return json.loads(payload) if payload else {}

    def download(self, path, out):
        _, _, payload = self.raw("GET", path)
        pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
        pathlib.Path(out).write_bytes(payload)
        return {"success": True, "file": str(out), "bytes": len(payload)}


# ── commands ─────────────────────────────────────────────────────────────────────────────────

def cmd_find(args):
    spec = json.loads(SPEC.read_text())
    words = [w.lower() for w in args.words]
    hits = []
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method not in ("get", "post", "put", "patch", "delete"):
                continue
            hay = " ".join([method, path, op.get("summary", ""), op.get("description", ""),
                            " ".join(op.get("tags", []))]).lower()
            if all(w in hay for w in words):
                hits.append({"method": method.upper(), "path": path, "summary": op.get("summary"),
                             "tag": (op.get("tags") or [""])[0]})
    return {"success": True, "count": len(hits), "data": hits}


def parse_data(value):
    if value is None:
        return None
    if value.startswith("@"):
        value = pathlib.Path(value[1:]).read_text()
    return json.loads(value)


def kv(pairs):
    out = {}
    for p in pairs or []:
        k, _, v = p.partition("=")
        out.setdefault(k, []).append(v)
    return {k: v[0] if len(v) == 1 else v for k, v in out.items()}


def cmd_fields(client, template_id):
    t = client.request("GET", f"/report-templates/{template_id}").get("data") or {}
    rows = []
    for f in t.get("userDefinedFields") or []:
        default = f.get("defaultValue")
        rows.append({
            "id": f.get("id"), "variableName": f.get("variableName"), "displayName": f.get("displayName"),
            "fieldType": f.get("fieldType"), "fieldScope": f.get("fieldScope"),
            "required": bool(f.get("required")),
            # the report prints the default when no value is stored, so only these need one from you
            "needsValue": bool(f.get("required")) and not (default and str(default).strip()),
            "defaultValue": default if default is None or len(str(default)) <= 120 else str(default)[:117] + "...",
            "dropdownOptions": f.get("dropdownOptions") or None,
        })
    return {"success": True, "template": t.get("name"), "count": len(rows),
            "needValue": [r["variableName"] for r in rows if r["needsValue"]], "data": rows,
            "note": "initialFieldValues (ASSESSMENT scope) and fieldValues (VULNERABILITY scope) are keyed "
                    "by id. A field left empty is printed with its defaultValue, so only needsValue fields "
                    "must be supplied."}


def leftover_placeholders(docx_path):
    """Any ${...} that survived into the report is a field nobody filled in."""
    found = set()
    with zipfile.ZipFile(docx_path) as z:
        for name in z.namelist():
            if re.search(r"word/(document|header\d*|footer\d*)\.xml$", name):
                text = re.sub(r"<[^>]+>", "", z.read(name).decode("utf-8", "replace"))
                found.update(re.findall(r"\$\{[^}]{1,80}\}", text))
    return sorted(found)


def cmd_generate(client, assessment_id, out_dir, types, timeout, prefix=None):
    client.request("POST", f"/reports/{assessment_id}/generate", data={})
    deadline = time.time() + timeout
    while True:
        resp = client.request("GET", f"/reports/{assessment_id}/documents").get("data") or {}
        docs = resp.get("documents") or []
        wanted = [d for d in docs if d.get("type") in types]
        if wanted and all(d.get("status") != "GENERATING" for d in wanted):
            break
        if time.time() > deadline:
            raise FactionError(504, f"report still generating after {timeout}s: "
                                    + ", ".join(f"{d.get('type')}={d.get('status')}" for d in docs))
        time.sleep(3)

    result = {"success": True, "assessmentId": assessment_id, "documents": []}
    if resp.get("reportPassword"):
        result["encryptedPdfPasswordAvailable"] = True     # fetch it deliberately; never echo it here
    for d in wanted:
        entry = {"type": d.get("type"), "status": d.get("status")}
        if d.get("status") == "COMPLETED" and out_dir:
            ext = "docx" if d["type"] == "DOCX" else "pdf"
            if prefix:
                # report.docx, report.pdf, report-encrypted.pdf — the two PDFs must not collide
                out = pathlib.Path(out_dir) / (f"{prefix}-encrypted.pdf" if d["type"] == "ENCRYPTED_PDF"
                                               else f"{prefix}.{ext}")
            else:
                out = pathlib.Path(out_dir) / f"{assessment_id}-{d['type'].lower()}.{ext}"
            client.download(f"/reports/{assessment_id}/documents/{d['type']}/content", out)
            entry["file"] = str(out)
            if d["type"] == "DOCX":
                entry["unresolvedPlaceholders"] = leftover_placeholders(out)
        elif d.get("errorMessage"):
            entry["error"] = d["errorMessage"]
        result["documents"].append(entry)
    return result


def main():
    ap = argparse.ArgumentParser(description="iSec Faction API client", epilog=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("whoami")
    p = sub.add_parser("find"); p.add_argument("words", nargs="+")
    p = sub.add_parser("call")
    p.add_argument("method"); p.add_argument("path")
    p.add_argument("--data"); p.add_argument("--query", action="append")
    p.add_argument("--all-pages", action="store_true")
    p = sub.add_parser("fields"); p.add_argument("template_id")
    p = sub.add_parser("upload")
    p.add_argument("path"); p.add_argument("--file", required=True)
    p.add_argument("--field", action="append"); p.add_argument("--file-field", default="file")
    p.add_argument("--type")
    p = sub.add_parser("download"); p.add_argument("path"); p.add_argument("--out", required=True)
    p = sub.add_parser("generate")
    p.add_argument("assessment_id"); p.add_argument("--out-dir")
    p.add_argument("--types", default="DOCX,PDF"); p.add_argument("--timeout", type=int, default=600)
    p.add_argument("--name", help="file name stem: report -> report.docx, report.pdf, report-encrypted.pdf")
    a = ap.parse_args()

    try:
        if a.cmd == "find":
            out = cmd_find(a)
        else:
            c = Client()
            if a.cmd == "whoami":
                out = c.request("GET", "/auth/me")
            elif a.cmd == "call":
                if a.all_pages and a.method.upper() == "GET":
                    out = c.all_pages(a.path, kv(a.query))
                else:
                    out = c.request(a.method, a.path, parse_data(a.data), kv(a.query))
            elif a.cmd == "fields":
                out = cmd_fields(c, a.template_id)
            elif a.cmd == "upload":
                out = c.upload(a.path, a.file, kv(a.field), a.file_field, a.type)
            elif a.cmd == "download":
                out = c.download(a.path, a.out)
            elif a.cmd == "generate":
                out = cmd_generate(c, a.assessment_id, a.out_dir,
                                   [t.strip().upper() for t in a.types.split(",")], a.timeout, a.name)
        print(json.dumps(out, indent=2, default=str))
    except FactionError as e:
        print(json.dumps({"success": False, "status": e.status, "message": e.message}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
