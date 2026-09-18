-- The client's distribution list: who the finished report goes to. See model/ClientContact.
--
-- The client's own people rather than accounts here, so this is recorded data with no
-- access-control meaning — naming someone on the list grants them nothing.
--
-- Guarded: Flyway runs before Hibernate's schema bootstrap, so on a fresh database the table does
-- not exist yet and an unguarded ALTER would fail app startup. There the entity definition creates
-- the column correctly anyway. The default matches the entity's @Builder.Default, so every existing
-- organization reads back as having an empty list rather than a null one.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'organizations') THEN
    ALTER TABLE organizations
        ADD COLUMN IF NOT EXISTS distribution_list JSONB NOT NULL DEFAULT '[]'::jsonb;
  END IF;
END $$;
