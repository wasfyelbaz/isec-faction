# iSec Mobile Application Penetration Testing template

The iSec MAPT report template converted for Faction 2. It is the template the running installation
actually generates from.

| File | What it is |
|---|---|
| `isec-mobile-original.docx` | The untouched iSec template the conversion started from. |
| `isec-mobile-upload.docx` | The file to upload to Faction. No Word comments, so none appear in a generated report. |
| [`MAPPING.md`](MAPPING.md) | Section-by-section mapping: the three report sections and eight fields it needs, what was deliberately left static, what Faction cannot express, and what would have to be built for the rest. |

`MAPPING.md` is unusually detailed about *why* each decision was made — its section D (mappings
deliberately not made) and section E (what cannot be expressed) are the clearest statement of the
engine's limits anywhere in this folder, and the general parts of both have been lifted into
[`../../AGENT.md`](../../AGENT.md) section 2.

## What is still missing

Compared with [`../web/`](../web/) and [`../network/`](../network/) this template has **no UDF
JSON and no annotated master or samples**. Worth closing when there is a reason to touch it again:

1. Export its user-defined fields to `isec-mobile-udfs.json`, as the other two have — they are
   specified in `MAPPING.md` section B but not in the API's shape.
2. Generate a sample into `samples/` so there is a reference render.

## Registered twice

The running installation has **two** report templates pointing at this one file —
"Mobile Application Pentest" and "iSec MAPT Report". Editing "the MAPT template" in the Report
Designer therefore changes only one of them, and which one is not obvious from the name. Decide
which is canonical and delete the other before making changes here.

## Stylesheet

Uses the shared [`../../shared/isec-report.css`](../../shared/isec-report.css), with the template's
Report Font set to Calibri alongside it.
