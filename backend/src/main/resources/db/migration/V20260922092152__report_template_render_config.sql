-- Per-template rendering options for the two placeholders that used to be served by
-- App Store extensions: the checklist tables and the severity bar chart. Both are now
-- rendered in-process, and each template carries its own labels, colours and sizing.
--
-- Deliberately no dollar-brace placeholder spellings anywhere in this file, including
-- comments: Flyway substitutes that syntax before the SQL is parsed, and naming the very
-- tokens this migration exists for fails the migration -- and with it every test that
-- starts a context -- with "No value provided for placeholder".
--
-- Guarded: Flyway runs before Hibernate's schema bootstrap, so on a fresh database
-- report_templates does not exist yet and an unguarded ALTER fails startup. On a fresh
-- install the entity definition creates these columns correctly anyway.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'report_templates') THEN
    ALTER TABLE report_templates
        ADD COLUMN IF NOT EXISTS checklist_config JSONB NOT NULL DEFAULT '{}'::jsonb;
    ALTER TABLE report_templates
        ADD COLUMN IF NOT EXISTS bar_chart_config JSONB NOT NULL DEFAULT '{}'::jsonb;
  END IF;
END $$;
