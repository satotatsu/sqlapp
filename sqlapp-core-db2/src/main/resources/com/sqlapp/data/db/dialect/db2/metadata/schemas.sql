SELECT
  s.SCHEMANAME AS SCHEMA_NAME
  , S.*
FROM SYSCAT.SCHEMATA s
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(SCHEMANAME) IN /*schemaName*/('%')
  /*end*/
ORDER BY SCHEMANAME
WITH UR

