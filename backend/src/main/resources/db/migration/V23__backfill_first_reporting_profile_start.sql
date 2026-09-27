-- An activity's first reporting profile assignment used to start on the day the activity was
-- created, leaving earlier dates uncovered: Schedule E reports for a year that began before then
-- failed with "reporting profile period is missing". V11 seeded pre-existing activities from
-- 0001-01-01, so extend every other activity's earliest assignment back to the same start.
UPDATE activity_reporting_profile_assignments assignment
SET effective_from = DATE '0001-01-01'
WHERE assignment.effective_from > DATE '0001-01-01'
  AND assignment.effective_from = (
      SELECT MIN(earliest.effective_from)
      FROM activity_reporting_profile_assignments earliest
      WHERE earliest.activity_id = assignment.activity_id);

-- Postcheck: every activity with assignments now has one covering the start of history.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT activity_id
    FROM activity_reporting_profile_assignments
    GROUP BY activity_id
    HAVING MIN(effective_from) <> DATE '0001-01-01'
) uncovered;
