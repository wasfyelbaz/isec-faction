# Report Fonts

Fonts for the PDF step, managed from the Report Designer instead of the server.

## The problem this solves

A generated report exists twice: as the DOCX the template engine fills in, and as the PDF that
"Preview Report" shows and the PDF download serves. The DOCX carries font *names* — the template's
own fonts plus the template's **Report Font**, which the engine writes on the text Faction
generates (findings, rich-text fields). Word on a workstation resolves those names from the fonts
installed there.

The PDF is different. It is drawn by LibreOffice on the backend, with the fonts installed in the
backend container. A name the container does not have is silently substituted; with the stock
image that meant DejaVu Sans for everything, whatever the template said.

## What the backend image ships

The image installs three families on top of LibreOffice's DejaVu:

| Family | Why |
| --- | --- |
| Carlito | Free, metric-compatible with Calibri; LibreOffice substitutes it for Calibri by itself |
| Liberation Sans / Serif / Mono | Free, metric-compatible with Arial, Times New Roman and Courier New |
| Calibri (regular, bold, italic, bold italic, light, light italic) | The Microsoft originals from `backend/fonts/`, so PDFs match Word exactly. Not freely redistributable — see `backend/fonts/README.md` |

## Uploading a font

Report Designer → select any template → **Custom CSS Formatting** → **PDF Fonts**.

- Upload TrueType or OpenType files (`.ttf`, `.otf`), up to 25 MB each, several at once. Upload
  every style you use (regular, bold, italic, bold italic) as its own file; `.ttc` collections are
  refused.
- The family and style are read from the font itself, not from the file name. Uploading a style
  that already exists replaces it.
- The list under the field shows every uploaded font. "Show the N font families available on the
  server" lists everything LibreOffice can draw with, bundled and uploaded alike.
- If a template's Report Font is not among the installed families, a warning says so: the PDF will
  fall back to a substitute until the font is uploaded.

Fonts are global: one upload serves every template.

## What happens on the server

The bytes go to object storage (`report-fonts/{id}/{file}`) and a `report_fonts` row records the
family, style and key. `ReportFontInstaller` then writes every row's file into
`faction.report-fonts.install-dir` (`REPORT_FONTS_DIR`, default
`/usr/share/fonts/truetype/faction-uploaded`), removes files no row accounts for, refreshes the
fontconfig cache and **restarts the headless LibreOffice server**. The same sync runs at startup,
so a container rebuilt from the image gets its fonts back from storage.

The restart matters. LibreOffice reads the font list once at startup, and the long-running server
is what lays out the table of contents when a report is generated: started before a font existed,
it computes page numbers in a substitute font while the PDF, converted by a fresh process that does
see the font, paginates differently. The application therefore owns the server
(`LibreOfficeServerManager`) rather than the container entrypoint. `LIBREOFFICE_SERVER_MANAGED=false`
opts out for installs that run `soffice` some other way.

## API

| Method | Path | Permission |
| --- | --- | --- |
| `GET` | `/api/v1/report-fonts` | `report_templates:read:all` |
| `GET` | `/api/v1/report-fonts/installed` | `report_templates:read:all` |
| `POST` | `/api/v1/report-fonts` (multipart, `files`) | `report_templates:edit:all` |
| `DELETE` | `/api/v1/report-fonts/{id}` | `report_templates:edit:all` |

The font bytes are never served back.
