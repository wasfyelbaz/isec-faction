# iSec Internal / External Network Penetration Testing template

The iSec Network report template converted to Faction 2, at v5. `../AGENT.md` explains the engine
rules and the conversion recipes; this folder holds what is specific to this template.

| File | What it is |
|---|---|
| `../Original/1._iSec_INT_EXTNWPT_Template.docx` | The untouched iSec template the conversion started from. |
| `../Faction Tuned/1._iSec_INT_EXTNWPT_Template_Faction.docx` | The file to upload to Faction. No Word comments, so none appear in a generated report. |
| `iSec_INT_EXTNWPT_Template_Faction_annotated.docx` | The same template carrying 19 "Faction Mapping" comments that explain each tag. Documentation master: do not upload it. |
| `Faction2_INT_EXTNWPT_Template_Mapping.md` | Section-by-section mapping, the 27 user-defined fields, the remaining gaps, and the v1 to v5 change history with how each version was verified. |
| `Faction_INT_EXTNWPT_UDF_Definitions.json` | The 27 fields in the API's `userDefinedFields` shape, ready to PUT onto a report template. |
| `../Faction_WAPT_Template.css` | The Report Designer stylesheet. Shared with the Web template; the Network template needs its scope-column rules. Set the template's Report Font to Calibri alongside it. |
| `samples/Sample_Network_Report_v5*` | A report generated from v5 on a simulated engagement: 3 findings, and a 13-row iSec checklist answered 8 PASS / 5 FAIL. Both charts, the CVSS links and the scope tables in it were verified against those numbers. |

## What it needs from the backend

Client images in headers and footers need `b77a81b` or later. The two native charts
(`${chartData severity}`, `${chartData checklist}`), the `${cvssLink <label>}` NVD link and the
guard that closes a table cell after replaced rich text need `5a9e5b5` or later.
