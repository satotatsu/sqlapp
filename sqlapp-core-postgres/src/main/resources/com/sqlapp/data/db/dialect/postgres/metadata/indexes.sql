SELECT
  current_database() AS catalog_name
, n.nspname AS schema_name
, ci.relname AS index_name
, ti.relname AS table_name
, i.indnatts
, i.indisunique AS is_unique
, i.indisprimary AS is_primary
, i.indexprs
, CASE WHEN i.indexprs IS NULL THEN a.attname
       ELSE pg_get_indexdef(ci.oid,a.attnum,false)
  END AS COLUMN_NAME
, am.amname AS index_type
, a.attnum AS num
, pg_get_indexdef(ci.oid) AS definition
, obj_description(i.indexrelid, current_database())
  AS remarks
FROM pg_catalog.pg_index i
INNER JOIN pg_catalog.pg_class ci
  on (i.indexrelid=ci.oid)
INNER JOIN pg_catalog.pg_class ti
  on (i.indrelid=ti.oid)
INNER JOIN pg_catalog.pg_namespace n
  on (ci.relnamespace = n.oid)
INNER JOIN pg_catalog.pg_am am
  on (ci.relam=am.oid)
INNER JOIN pg_catalog.pg_attribute a
  on (a.attrelid=ci.oid) 
WHERE 0=0
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND ti.relname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND ci.relname IN /*indexName*/('%')
  /*end*/
ORDER BY n.nspname, ci.relname, a.attnum