-- This fork calls an organization a client, so that is what the screens say out of the box.
--
-- Only the wording moves: the entity, the `organizations` table, the /api/v1/organizations routes
-- and every stored row are untouched, which is what keeps the fork mergeable with upstream.
--
-- Existing installations already hold a terminology row carrying the product's old defaults, and a
-- default only applies to an INSERT — so the row is rewritten here as well. Strictly guarded on the
-- old default value: an installation that already renamed these to its own wording ("Value Stream",
-- "Business Unit") chose that deliberately, and a release must never silently undo it.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'terminology_config') THEN
    ALTER TABLE terminology_config
        ALTER COLUMN organization_singular SET DEFAULT 'Client';
    ALTER TABLE terminology_config
        ALTER COLUMN organization_plural SET DEFAULT 'Clients';

    UPDATE terminology_config
       SET organization_singular = 'Client'
     WHERE organization_singular = 'Organization';

    UPDATE terminology_config
       SET organization_plural = 'Clients'
     WHERE organization_plural = 'Organizations';
  END IF;
END $$;
