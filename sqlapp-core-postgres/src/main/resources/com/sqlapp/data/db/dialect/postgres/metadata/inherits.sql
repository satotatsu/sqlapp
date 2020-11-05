SELECT 
  current_database() AS catalog_name
  , n.nspname AS schema_name
  , c.relname AS table_name
  , pn.nspname AS parent_schema_name
  , pc.relname AS parent_table_name
  , i.inhseqno
FROM pg_inherits i
INNER JOIN pg_catalog.pg_class c
  ON (i.inhrelid = c.oid)
INNER JOIN pg_catalog.pg_namespace n
  ON (c.relnamespace = n.oid)
INNER JOIN pg_catalog.pg_class pc
  ON (i.inhparent = pc.oid)
INNER JOIN pg_catalog.pg_namespace pn
  ON (pc.relnamespace = pn.oid)
WHERE c.relkind IN ('r', 'p')
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.relname IN /*tableName*/('%')
  /*end*/
ORDER BY i.inhrelid, i.inhseqno