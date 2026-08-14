SELECT
    r.ROUTINESCHEMA AS schema_name
  , r.ROUTINENAME AS routine_name
  , r.SPECIFICNAME AS specific_name
  , r.*
FROM SYSCAT.ROUTINEPARMS r
WHERE EXISTS (
  SELECT 1 FROM SYSCAT.ROUTINES x
  WHERE x.ROUTINESCHEMA=r.ROUTINESCHEMA
    AND x.SPECIFICNAME=r.SPECIFICNAME
    AND x.ROUTINETYPE='F'
)
  /*if isNotEmpty(schemaName) */
  AND rtrim(r.ROUTINESCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND rtrim(r.ROUTINENAME) IN /*functionName*/('%')
  /*end*/
ORDER BY r.ROUTINESCHEMA, r.ROUTINENAME, r.SPECIFICNAME, r.ORDINAL
WITH UR  
