-- A client's images, held in named slots: logo, cover, signature, or whatever a template asks for.
--
-- Its own table rather than another JSONB column on `organizations`, because unlike the
-- distribution list a row here is a pointer into object storage — the bytes live in MinIO and this
-- records where. Keeping them separate is what lets an image be replaced, or a client's images be
-- cleaned up on delete, without rewriting the organization row.
CREATE TABLE IF NOT EXISTS organization_images (
    id                  VARCHAR(255) PRIMARY KEY,
    organization_id     VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    original_file_name  VARCHAR(255),
    content_type        VARCHAR(255),
    file_size           BIGINT,
    uploaded_by         VARCHAR(255),
    uploaded_at         TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_organization_images_organizationid
    ON organization_images (organization_id);

-- One image per slot per organization, not globally: every client has a "logo". Uploading again to
-- the same name replaces what was there, so everything pointing at the slot follows automatically.
CREATE UNIQUE INDEX IF NOT EXISTS idx_organization_images_org_name
    ON organization_images (organization_id, name);
