SELECT
    m.*
  , pg_catalog.obj_description(c.oid, 'pg_class') AS remarks
FROM pg_catalog.pg_matviews m
JOIN pg_catalog.pg_namespace n ON n.nspname = m.schemaname
JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid
  AND c.relname = m.matviewname AND c.relkind = 'm'
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND m.schemaname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND m.matviewname IN /*tableName*/('%')
  /*end*/
ORDER BY m.schemaname, m.matviewname
