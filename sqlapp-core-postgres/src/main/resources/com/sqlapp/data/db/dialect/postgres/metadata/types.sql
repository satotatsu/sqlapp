SELECT
  current_database() AS type_catalog
, n.nspname AS type_schema
, t.typname AS type_name
, CAST(t.oid AS int4) AS oid
, pg_get_ruledef(t.oid) AS definition
, t.typdefault
, obj_description(t.oid, current_database()) as remarks
FROM pg_catalog.pg_type t
INNER JOIN pg_catalog.pg_namespace n
  ON (t.typnamespace = n.oid)
INNER JOIN pg_catalog.pg_class c
  ON (t.typrelid = c.oid
 AND t.typtype = c.relkind)
WHERE 1=1
  AND t.typtype = 'c'
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(typeName) */
  AND t.typname IN /*typeName*/('%')
  /*end*/
ORDER BY n.nspname, t.typname
