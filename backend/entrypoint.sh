#!/bin/sh
set -e

# LibreOffice is no longer started here. The application launches the headless server itself
# (LibreOfficeServerManager) once the uploaded report fonts are on disk, and restarts it whenever
# a font is added or removed: a server started before the fonts existed lays the table of contents
# out in substitute fonts and gets every page number wrong. Set LIBREOFFICE_SERVER_MANAGED=false to
# opt out and start soffice some other way.

# Start the Spring Boot application
exec java -jar app.jar
