SELECT
  current_database() AS catalog_name
, n.nspname AS schema_name
, ci.relname AS index_name
, ti.relname AS table_name
, i.indnatts
, i.indnatts AS indnkeyatts
, false AS indnullsnotdistinct
, i.indisunique AS is_unique
, i.indisprimary AS is_primary
, i.indexprs
, CASE WHEN i.indexprs IS NULL THEN a.attname
       ELSE pg_get_indexdef(ci.oid,a.attnum,false)
  END AS COLUMN_NAME
, am.amname AS index_type
, a.attnum AS num
, pg_get_indexdef(ci.oid) AS definition
, obj_description(i.indexrelid, 'pg_class')
  AS remarks, pg_get_indexdef(ci.oid,a.attnum,false)
 || COALESCE((SELECT ' COLLATE ' || quote_ident(ns.nspname) || '.' || quote_ident(co.collname) FROM pg_catalog.pg_collation co JOIN pg_catalog.pg_namespace ns ON ns.oid=co.collnamespace WHERE co.oid=i.indcollation[a.attnum-1]),'')
 || COALESCE((SELECT ' ' || quote_ident(ns.nspname) || '.' || quote_ident(op.opcname)
     FROM pg_catalog.pg_opclass op JOIN pg_catalog.pg_namespace ns ON ns.oid=op.opcnamespace
     WHERE op.oid=i.indclass[a.attnum-1]),'')
 || CASE WHEN am.amname IN ('btree','lsm') AND a.attnum <= i.indnatts THEN
      CASE WHEN (i.indoption[a.attnum-1] & 1)=1 THEN ' DESC' ELSE ' ASC' END
   || CASE WHEN (i.indoption[a.attnum-1] & 2)=2 THEN ' NULLS FIRST' ELSE ' NULLS LAST' END ELSE '' END AS key_sql, ci.reloptions AS index_options,
 (SELECT spcname FROM pg_catalog.pg_tablespace WHERE oid=ci.reltablespace) AS index_tablespace
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
