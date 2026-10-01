# Report templates

The iSec report templates converted for Faction, the documents that explain how they were made,
and the stylesheet they share.

## Where to start

| If you want to | Read |
|---|---|
| Convert a new Word document for Faction | [`CONVERSION_WORKFLOW.md`](CONVERSION_WORKFLOW.md) — the whole process |
| Know what a variable does, or where a tag may be placed | [`AGENT.md`](AGENT.md) — the engine reference |
| Know what Faction cannot do yet | [`BACKEND_GAPS.md`](BACKEND_GAPS.md) |
| Work on one specific template | `templates/<type>/README.md` |

## The templates

| Template | Folder | Version | Assessment type | Registered in Faction |
|---|---|---|---|---|
| Web application (WAPT) | [`templates/web/`](templates/web/) | v9 | Web Application Pentest | yes — "iSec WAPT Report" |
| Internal/external network (INT_EXTNWPT) | [`templates/network/`](templates/network/) | v6 | Network Assessment | **no — converted but never uploaded** |
| Mobile application (MAPT) | [`templates/mobile/`](templates/mobile/) | — | Mobile Application Pentest | yes, **twice** — "Mobile Application Pentest" and "iSec MAPT Report", both pointing at the same file |

Two things in that table are worth fixing: the Network template is finished and verified but has
never been uploaded, and Mobile is registered twice off one file, so editing "one of them" will
surprise somebody.

## Layout

```
report-templates/
├── README.md                 this file
├── AGENT.md                  engine reference — what the tags do
├── CONVERSION_WORKFLOW.md    process — how a conversion is run
├── BACKEND_GAPS.md           what Faction cannot do yet, and the shared UDF names
├── shared/
│   └── isec-report.css       the Report Designer stylesheet, shared by every template
└── templates/<type>/
    ├── README.md                   what each file is, what it needs from the backend
    ├── MAPPING.md                  section-by-section mapping, gaps, version history
    ├── isec-<type>-udfs.json       the user-defined fields, ready to PUT
    ├── isec-<type>-original.docx   the untouched iSec document
    ├── isec-<type>-upload.docx     THE FILE YOU UPLOAD — no Word comments
    ├── isec-<type>-annotated.docx  the same template + a comment at every decision
    └── samples/
        └── v<n>-report.docx / .pdf a generated report at that version
```

## Naming

- Lowercase, hyphens, no spaces. A space in a path is a quoting bug waiting to happen.
- The folder is the engagement type in plain words (`web`, `network`, `mobile`, and `ad`,
  `wireless`, `redteam` when they arrive), matching how Faction's assessment types read. The short
  codes your team says out loud — WAPT, MAPT, INT_EXTNWPT — are in the table above, so both are
  searchable.
- Every template folder carries **the same six filenames** with the type swapped in. You always
  know where to look, and a script never has to guess.
- `-upload` and `-annotated` are in the filenames, not just implied by a folder, because the one
  mistake that costs money is uploading the annotated master: **Word comments survive into every
  generated report.** The file picker should tell you which is which.
- **No version in the template filename.** Git versions the template, and `MAPPING.md` records what
  changed; renaming on every revision would break `git log --follow` and leave stale files behind.
  Samples are the exception — you want several side by side, so they carry `v<n>`.

## Completeness

Web and Network have the full set. Mobile has its template, its README and a detailed `MAPPING.md`,
but no UDF JSON and no annotated master or samples — see
[`templates/mobile/README.md`](templates/mobile/README.md).
