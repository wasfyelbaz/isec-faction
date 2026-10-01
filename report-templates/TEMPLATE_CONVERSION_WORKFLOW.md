# Converting a Word report template for Faction — the working agreement

You hand over a `.docx` that knows nothing about Faction. You get back a template Faction fills
with real engagement data, plus the field definitions and the stylesheet that make it work.

This file is the **handshake**: what you provide, what I do, where I stop and ask, and what
"finished" means. It is deliberately short on engine detail — that lives in
[`AGENT.md`](AGENT.md), which is the reference and always wins where the two disagree.

Three templates have been through this: **WAPT** (Web, v9), **MAPT** (Mobile) and
**INT_EXTNWPT** (Network, v6). Their folders are the worked examples.

---

## 0. The one rule that shapes everything

**Never fake a mapping.** If Faction has no variable for something the document says, it stays
static text or becomes a user-defined field, and the gap gets written down. A look-alike variable
that is nearly right is worse than an honest gap, because it produces a report that is confidently
wrong and nobody checks it again.

`${impact}` is a rating, not a paragraph. I will not use it as one.

---

## 1. What you give me

**Required**

| | |
|---|---|
| The document | The original `.docx`, exactly as your team uses it today. Not a cleaned-up copy — I need to see the real tables, text boxes, numbering and headers. |
| The engagement type | Web, Network, Mobile, Active Directory, Wireless, Red Team… This decides the assessment type it binds to and what the findings section has to carry. |

**Helpful, not required**

- A filled-in example of the same report from a past engagement, so I can see what each blank
  normally holds. This is the single most useful extra thing you can provide.
- Your house CSS, if you have one. Otherwise it starts from `Faction_WAPT_Template.css`.
- Whether the client logo should appear, and where.

**What I do not need:** you don't need to mark anything up, remove anything, or guess which parts
Faction can fill. Working that out is the job.

---

## 2. What comes back

```
report-templates/
├── Original/<Name>.docx                     your file, untouched, for reference
├── Faction Tuned/<Name>_Faction.docx        THE UPLOAD FILE — no comments
└── <Type>/
    ├── <Name>_Faction_annotated.docx        same template + a comment at every decision
    ├── Faction2_<Name>_Mapping.md           section-by-section mapping, gaps, version history
    ├── Faction_<Name>_UDF_Definitions.json  the user-defined fields, ready to PUT
    ├── README.md                            what each file is, what it needs from the backend
    └── samples/                             a generated report, DOCX + PDF
```

**Two files per template, and only one of them is ever uploaded.** Word comments survive into
every generated report, so the annotated master is documentation and must never reach Faction.
The upload copy is comment-free and verified so before it ships.

---

## 3. The phases

### Phase 1 — Read the document (no changes yet)

I inventory every section, placeholder, repeated structure, image, text box, header, footer, table
style and numbering definition, and render the original to PDF as the reference picture. Then
every field in the document is sorted into exactly one of four buckets:

| Bucket | Meaning |
|---|---|
| **Static** | Boilerplate. Stays as typed. |
| **Built-in** | Faction already has a variable — client name, dates, assessors, findings, CVSS, severity counts. |
| **UDF** | Faction has no variable, but the data is per engagement, so it becomes a user-defined field you fill in on the assessment. |
| **Gap** | Faction cannot represent it at all. Recorded, not faked. |

**→ Checkpoint 1.** You get the mapping before I touch the document. This is the cheapest moment
to correct me, and the one where your knowledge of what the fields actually mean matters most.

### Phase 2 — Set Faction up

The assessment type, the user-defined fields, the stylesheet and the report template record. The
UDF set is deliberately shared across templates where the meaning is the same (`limitations`,
`recommendations`, `project_name`), so an assessor filling in a Network report meets the same field
names they already know from a Web one.

**→ Checkpoint 2** if new UDFs are needed: you approve the names, types and defaults before they
exist, because renaming one later means editing every template that references it.

### Phase 3 — Convert the document

Scripted, not hand-edited, so it is repeatable and reviewable: placeholders become tags, the
findings table collapses to a single loop row, the findings body to a single block, cover and
footer boxes get the client-logo recipe, page-number frames are rebuilt, notes pages and
highlights come out.

Three things that are always wrong the first time, and are checked every time:

- **Table borders.** LibreOffice — which is what Faction renders with — writes an empty
  `<w:tcBorders/>` over every cell of any table on the `TableGridLight` style and drops
  table-level borders with it. The PDF looks right and the DOCX comes out with no lines at all.
  Every table goes on `TableGrid` with explicit per-cell borders.
- **Cover text boxes.** They clip the moment real content is longer than the sample text. Widened
  for the longest plausible value, not the one in front of me.
- **Fonts.** The PDF must list your family, not LibreOffice's Carlito/DejaVu substitutes.

### Phase 4 — Generate and measure

A throwaway client, target and assessment with deliberately awkward data: long values, short
values, findings across every severity, rich text with tables and screenshots, a finding with
several assets. Then the generated report is **measured, not glanced at**:

- Zero unresolved `${…}` anywhere in the document, headers or footers.
- Severity tallies agree with the findings.
- Logos sit inside their boxes, at the right size, for a square logo *and* a very wide one.
- Hyperlinks read out of the PDF's link annotations.
- Column widths and border colours read out of the PDF's drawings.
- The DOCX opened in Word **and** the PDF, side by side with the original.

**A correct PDF does not prove the DOCX, and a correct DOCX does not prove the PDF.** They come
from different models and they disagree, most often about table borders. Both get checked.

### Phase 5 — Hand over

Upload copy, annotated master, mapping document, UDF JSON, a generated sample, and the version
history recording what changed and how it was verified.

**→ Checkpoint 3.** You read a real generated report before it is used on a real engagement.

---

## 4. Where this usually goes wrong

Honest expectations, from the three conversions so far:

- **It takes several versions.** The Web template reached v9, Network v6. Each version fixed
  something only a rendered report could reveal. Budget for iteration, not a single pass.
- **The first generated report will look wrong somewhere.** That is the process working — it is
  far cheaper to find it here than on a client deliverable.
- **Some things genuinely cannot be mapped**, and you will get a list rather than a workaround.
- **A template referencing a feature the backend lacks will fail quietly.** Client images in
  headers and footers needed an engine change before they worked at all.

---

## 5. What I need decided, not guessed

I will stop and ask about these rather than pick for you:

1. **Anything ambiguous in the mapping** — when a heading could plausibly be two different
   variables, your answer decides it.
2. **New UDF names and defaults**, because they are shared across templates.
3. **Scope cuts** — if a section would need engine work, you choose: static text, a UDF, or a
   change request against the backend.
4. **Anything that would change the application's source code.** Per `CLAUDE.md` that needs your
   explicit go-ahead every time, with what / why / blast radius / alternative stated first. The
   template is the product: layout gets fixed in the document wherever it possibly can be.

---

## 6. Quick reference

| You want | Say |
|---|---|
| Start a conversion | "Convert this template for Faction" + the `.docx` + the engagement type |
| A new version of an existing one | "Template X needs …" — it goes to v(n+1) with history |
| Just the mapping, no conversion | "Map this template, don't convert it" — stops after Phase 1 |
| Know what Faction can't do | Ask for the gaps list from the mapping document |

**Companion files:** [`AGENT.md`](AGENT.md) is the engine reference — variables, rules, recipes and
the lessons behind them. `Web/`, `Network/` and the mapping documents are the worked examples;
read one alongside its template before starting a new conversion.
