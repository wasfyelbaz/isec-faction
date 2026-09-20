FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy vendored jars (system-scoped dependencies)
COPY lib ./lib

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre-jammy

# Release version stamped by the CI build (see .github/workflows/release.yml);
# surfaced by the public /api/v1/status endpoint.
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}

WORKDIR /app

# Install wget (health checks), tini (reaps the LibreOffice processes the application spawns and
# restarts; as PID 1 the JVM would leave them as zombies), LibreOffice headless (TOC refresh via
# UNO, DOCX to PDF) and the fonts the PDF step draws with.
#
# --no-install-recommends leaves LibreOffice with DejaVu alone, and a report set in Calibri, Arial
# or Times New Roman came out of the PDF in DejaVu Sans whatever the template said. Carlito and
# Liberation are the free, metric-compatible stand-ins LibreOffice substitutes for Calibri, Arial,
# Times New Roman and Courier New by itself; fonts/ carries the real Calibri family on top so PDFs
# match Word exactly. Fonts uploaded from the Report Designer are installed at runtime into
# REPORT_FONTS_DIR (see ReportFontInstaller); nothing here needs rebuilding to add one.
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget tini libreoffice-writer \
        fontconfig fonts-crosextra-carlito fonts-liberation && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY fonts/ /usr/share/fonts/truetype/faction-bundled/
RUN fc-cache -f

# Copy the built jar from build stage.
#
# Specifically the -boot jar. target/ holds two: the plain library jar that the enterprise
# overlay compiles against, and this repackaged runnable one. A bare *.jar glob picks the
# library jar and the container dies with "no main manifest attribute, in app.jar".
COPY --from=build /app/target/*-boot.jar app.jar

# Entrypoint script starts the Spring app; the app starts (and restarts) LibreOffice itself
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "./entrypoint.sh"]
