SELECT
    r.ROUTINESCHEMA AS schema_name
  , r.ROUTINENAME AS routine_name
  , r.SPECIFICNAME AS specific_name
  , r.*
FROM SYSCAT.ROUTINEPARMS r
WHERE r.ROUTINEMODULEID IS NULL
  AND r.ROWTYPE IN ('B', 'O', 'P') AND EXISTS (
  SELECT 1 FROM SYSCAT.ROUTINES x
  WHERE x.ROUTINESCHEMA=r.ROUTINESCHEMA
    AND x.SPECIFICNAME=r.SPECIFICNAME
    AND x.ROUTINETYPE='F'
)
  /*if isNotEmpty(schemaName) */
  AND rtrim(r.ROUTINESCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND rtrim(r.ROUTINENAME) IN /*routineName*/('%')
  /*end*/
ORDER BY r.ROUTINESCHEMA, r.ROUTINENAME, r.SPECIFICNAME, r.ORDINAL
WITH UR
