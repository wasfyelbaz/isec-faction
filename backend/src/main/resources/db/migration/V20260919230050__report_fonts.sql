-- Fonts uploaded from the Report Designer for the PDF step.
--
-- A PDF can only be drawn in a font that exists on the server, and the "Report Font" a template
-- names is otherwise just a name. Each row is a pointer into object storage, like an organization
-- image: the bytes live in MinIO and ReportFontInstaller writes them into the server's font
-- directory at startup and whenever the set changes, so a container rebuilt from the image gets
-- its fonts back from storage rather than losing them.
CREATE TABLE IF NOT EXISTS report_fonts (
    id                  VARCHAR(255) PRIMARY KEY,
    family              VARCHAR(255) NOT NULL,
    style               VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    original_file_name  VARCHAR(255),
    content_type        VARCHAR(255),
    file_size           BIGINT,
    uploaded_by         VARCHAR(255),
    uploaded_at         TIMESTAMP
);

-- One file per family and style, read from the font itself ("Calibri" + "Bold"). Uploading the
-- same style again replaces it, so a corrected file never sits beside the one it was meant to fix.
CREATE UNIQUE INDEX IF NOT EXISTS idx_report_fonts_family_style
    ON report_fonts (family, style);
