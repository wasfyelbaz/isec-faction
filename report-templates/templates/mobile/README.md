# iSec Mobile Application Penetration Testing template

The iSec MAPT report template converted for Faction 2. It is the template the running installation
actually generates from.

| File | What it is |
|---|---|
| `isec-mobile-original.docx` | The untouched iSec template the conversion started from. |
| `isec-mobile-upload.docx` | The file to upload to Faction. No Word comments, so none appear in a generated report. |

## What is missing, and why

This template was converted before the others and never got their supporting documents. Compared
with [`../web/`](../web/) and [`../network/`](../network/) it has **no `MAPPING.md`, no UDF JSON,
no annotated master and no samples**.

Its conversion notes exist on disk as `REPORT_TEMPLATE_UPDATES.md` in this folder, but that file is
listed in [`.gitignore`](../../../.gitignore) as a deliberately untracked working file, so it is
not in the repository. Anyone cloning this repo gets the two documents above and nothing else
explaining them.

Worth closing when there is a reason to touch this template again:

1. Promote the notes to a committed `MAPPING.md`, or write one from the template itself.
2. Export its user-defined fields to `isec-mobile-udfs.json`, as the other two have.
3. Generate a sample into `samples/` so there is a reference render.

## Registered twice

The running installation has **two** report templates pointing at this one file —
"Mobile Application Pentest" and "iSec MAPT Report". Editing "the MAPT template" in the Report
Designer therefore changes only one of them, and which one is not obvious from the name. Decide
which is canonical and delete the other before making changes here.

## Stylesheet

Uses the shared [`../../shared/isec-report.css`](../../shared/isec-report.css), with the template's
Report Font set to Calibri alongside it.
