# iSec Web Application Penetration Testing template

The iSec WAPT report template converted to Faction 2, at v9. It is the reference implementation
for every recipe in `../AGENT.md`; this folder holds what is specific to it.

| File | What it is |
|---|---|
| `../Original/1._iSec_WAPT_Template.docx` | The untouched iSec template the conversion started from. |
| `../Faction Tuned/1._iSec_WAPT_Template_Faction.docx` | The file to upload to Faction. No Word comments, so none appear in a generated report. |
| `iSec_WAPT_Template_Faction_annotated.docx` | The same template carrying 19 "Faction Mapping" comments that explain each tag and gap. Documentation master: do not upload it. |
| `Faction2_WAPT_Template_Mapping.md` | The complete built-in variable reference, the section-by-section mapping, the client package and the v1 to v9 history. |
| `Faction_UDF_Definitions.json` | The 21 user-defined fields in the API's `userDefinedFields` shape, ready to PUT onto a report template. |
| `Faction_Backend_Changes_Needed.md` | What Faction could not do when the template was converted, the engine fixes made for it, and the unified UDF names shared with the other templates. |
| `../Faction_WAPT_Template.css` | The Report Designer stylesheet (shared with the Network template). Set the template's Report Font to Calibri alongside it. |
| `samples/Sample_Web_Report_v9*` | A report generated from v9 on the OneBank client demo (3 findings): cover and footer logos through `${clientImage}`, page numbers, Calibri. |

## What it needs from the backend

Client images in headers and footers, with the fixed `width= height=` box, need `b77a81b` or
later. Every table is on `TableGrid` with its own 0.5 pt `BFBFBF` cell borders, the form whose
lines survive LibreOffice in the DOCX (AGENT.md lesson 7).
