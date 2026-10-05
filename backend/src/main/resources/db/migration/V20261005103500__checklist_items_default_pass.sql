-- Checklist items now start Not Vulnerable (PASS) instead of unanswered: findings filed under an
-- item turn it Vulnerable, and N/A is set by hand, so an untouched checklist reads as tested and
-- clean. New checklists get PASS from AssessmentChecklistService; this brings the ones already
-- attached to assessments still in progress into line by setting their unanswered items to PASS.
--
-- Completed assessments (completed_date set) are left alone, so a delivered report does not change
-- if it is regenerated. Answered items (PASS, FAIL, NA) are never touched.
--
-- Guarded on both tables existing, because Flyway runs before Hibernate's schema bootstrap and they
-- are not there yet on a fresh database — where there is nothing to backfill anyway.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'assessment_checklists')
     AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'assessments') THEN
    UPDATE assessment_checklists ac
       SET responses = (
             SELECT jsonb_agg(
                      CASE WHEN r->>'result' IS NULL
                           THEN jsonb_set(r, '{result}', '"PASS"'::jsonb, true)
                           ELSE r END
                      ORDER BY ord)
               FROM jsonb_array_elements(ac.responses::jsonb) WITH ORDINALITY AS t(r, ord))
     WHERE ac.responses IS NOT NULL
       AND jsonb_typeof(ac.responses::jsonb) = 'array'
       AND EXISTS (SELECT 1 FROM jsonb_array_elements(ac.responses::jsonb) r WHERE r->>'result' IS NULL)
       AND EXISTS (SELECT 1 FROM assessments a
                    WHERE a.id = ac.assessment_id AND a.completed_date IS NULL);
  END IF;
END $$;
