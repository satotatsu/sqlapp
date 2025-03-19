SELECT
  current_database() AS function_catalog
, n.nspname AS function_schema
, p.proname AS function_name
, l.lanname
, p.oid
, CASE when prokind='a' THEN null
  ELSE pg_get_functiondef(p.oid)
  END AS functiondef
, pg_get_function_arguments(p.oid) AS function_arguments
, pg_get_function_identity_arguments(p.oid) AS function_identity_arguments
, pg_get_function_result(p.oid) AS function_result
, p.*
FROM pg_catalog.pg_proc p
INNER JOIN pg_catalog.pg_namespace n
 ON (p.pronamespace=n.oid)
INNER JOIN pg_catalog.pg_language l
 ON (p.prolang = l.oid)
INNER JOIN pg_catalog.pg_type t
 ON (p.prorettype = t.oid)
WHERE 0=0
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND p.proname IN /*functionName*/('%')
  /*end*/
ORDER BY n.nspname, p.proname