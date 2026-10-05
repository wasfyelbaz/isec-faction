-- Assessment names are now Client + Target + Assessment Type ("Network International TMS Web
-- Application Pentest"), set by AssessmentService on every create and save rather than typed. This
-- renames the assessments that already exist to match.
--
-- Completed assessments (completed_date set) keep their names, so a delivered report does not change
-- if it is regenerated. Deleted ones are left alone. Parts that cannot be resolved are skipped, and a
-- name is only replaced when at least one part is found.
--
-- Guarded on the tables existing, because Flyway runs before Hibernate's schema bootstrap and they
-- are not there yet on a fresh database — where there is nothing to rename anyway.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'assessments')
     AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'applications')
     AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'organizations')
     AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'assessment_types') THEN
    UPDATE assessments a
       SET name = n.standard
      FROM (
        SELECT a2.id,
               NULLIF(concat_ws(' ',
                        NULLIF(btrim(o.name), ''),
                        NULLIF(btrim(app.name), ''),
                        NULLIF(btrim(t.name), '')), '') AS standard
          FROM assessments a2
          LEFT JOIN applications app ON app.id = a2.application_id
          LEFT JOIN organizations o ON o.id = app.organization_id
          LEFT JOIN assessment_types t ON t.id = a2.assessment_type_id
      ) n
     WHERE n.id = a.id
       AND n.standard IS NOT NULL
       AND a.completed_date IS NULL
       AND a.deleted_at IS NULL
       AND a.name IS DISTINCT FROM n.standard;
  END IF;
END $$;
