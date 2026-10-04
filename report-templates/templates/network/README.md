# iSec Internal / External Network Penetration Testing template

The iSec Network report template converted to Faction 2, at v7. `../../AGENT.md` explains the engine
rules and the conversion recipes; this folder holds what is specific to this template.

| File | What it is |
|---|---|
| `isec-network-original.docx` | The untouched iSec template the conversion started from. |
| `isec-network-upload.docx` | The file to upload to Faction. No Word comments, so none appear in a generated report. |
| `isec-network-annotated.docx` | The same template carrying 19 "Faction Mapping" comments that explain each tag. Documentation master: do not upload it. |
| `MAPPING.md` | Section-by-section mapping, the 27 user-defined fields, the remaining gaps, and the v1 to v7 change history with how each version was verified. |
| `isec-network-udfs.json` | The 27 fields in the API's `userDefinedFields` shape, ready to PUT onto a report template. |
| `isec-network-checklists.json` | The 13-item "iSec Network Penetration Testing Checklist" from section 4.1 of the original, read by the 4.1 table and the 2.4 chart. It exists in Faction as a checklist template for the Network Assessment type; attach it to an assessment and answer it for both to show real results. |
| `../../shared/isec-report.css` | The Report Designer stylesheet. Shared with the Web template; the Network template needs its scope-column rules. Set the template's Report Font to Calibri alongside it. |
| `samples/v6-report.*` | A report generated from v6 on a simulated engagement: 3 findings, and a 13-row iSec checklist answered 8 PASS / 5 FAIL. Both charts, the CVSS links and the scope tables in it were verified against those numbers. |

## What it needs from the backend

Client images in headers and footers need `b77a81b` or later. The two native charts
(`${chartData severity}`, `${chartData checklist}`), the `${cvssLink <label>}` NVD link and the
guard that closes a table cell after replaced rich text need `5a9e5b5` or later. v6 keeps every table on `TableGrid` with a `BFBFBF` border on each cell, the only form whose lines survive LibreOffice in the DOCX (AGENT.md lesson 7).
