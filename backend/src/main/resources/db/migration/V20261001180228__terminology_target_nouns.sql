-- What this install calls the thing an engagement runs against.
--
-- Faction's entity is an "application", which suits a software shop. A security consultancy tests
-- networks, Active Directory forests, wireless estates and cloud tenants as readily as
-- applications, so the screens say "target" instead.
--
-- Only the wording moves: the entity, the `applications` table, the /api/v1/applications routes
-- and every stored row are untouched, which is what keeps the fork mergeable with upstream.
--
-- Guarded on the table existing, because Flyway runs before Hibernate's schema bootstrap and the
-- table is not there yet on a fresh database — the entity definition creates these columns with
-- the same defaults in that case. NOT NULL with a default so the row an existing installation
-- already holds gets the wording rather than a null.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'terminology_config') THEN
    ALTER TABLE terminology_config
        ADD COLUMN IF NOT EXISTS target_singular VARCHAR(255) NOT NULL DEFAULT 'Target';
    ALTER TABLE terminology_config
        ADD COLUMN IF NOT EXISTS target_plural VARCHAR(255) NOT NULL DEFAULT 'Targets';
  END IF;
END $$;
