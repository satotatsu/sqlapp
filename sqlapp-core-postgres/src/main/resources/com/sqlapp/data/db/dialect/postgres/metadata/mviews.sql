SELECT
    m.*
  , null AS remarks
FROM pg_catalog.pg_matviews m
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND m.schemaname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND m.matviewname IN /*tableName*/('%')
  /*end*/
ORDER BY m.schemaname, m.matviewname