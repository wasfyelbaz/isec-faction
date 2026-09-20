# Bundled report fonts

Every file in this directory is copied into the backend image at
`/usr/share/fonts/truetype/faction-bundled/` (see `Dockerfile`) so LibreOffice can draw PDFs in it.

The image also installs the free **Carlito** and **Liberation** families from Ubuntu, which
LibreOffice substitutes for Calibri, Arial, Times New Roman and Courier New by itself. The Calibri
files here (`calibri*.ttf`: regular, bold, italic, bold italic, light, light italic) are the
Microsoft originals from a licensed Windows installation, added so PDFs match Word exactly. They
are not freely redistributable: keep this repository private, or remove them and rely on Carlito.

Fonts that should not live in the image are uploaded from the Report Designer instead (Custom CSS
Formatting → PDF Fonts) and installed at runtime.
