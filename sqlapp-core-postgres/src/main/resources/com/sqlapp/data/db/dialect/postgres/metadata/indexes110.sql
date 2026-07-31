SELECT current_database() AS catalog_name,n.nspname AS schema_name,
 ci.relname AS index_name,ti.relname AS table_name,i.indnatts,i.indnkeyatts,
 false AS indnullsnotdistinct,i.indisunique AS is_unique,i.indisprimary AS is_primary,
 i.indexprs,CASE WHEN i.indexprs IS NULL THEN a.attname ELSE pg_get_indexdef(ci.oid,a.attnum,false) END AS column_name,
 am.amname AS index_type,a.attnum AS num,pg_get_indexdef(ci.oid) AS definition,
 obj_description(i.indexrelid,current_database()) AS remarks
FROM pg_catalog.pg_index i
JOIN pg_catalog.pg_class ci ON i.indexrelid=ci.oid
JOIN pg_catalog.pg_class ti ON i.indrelid=ti.oid
JOIN pg_catalog.pg_namespace n ON ci.relnamespace=n.oid
JOIN pg_catalog.pg_am am ON ci.relam=am.oid
JOIN pg_catalog.pg_attribute a ON a.attrelid=ci.oid
WHERE 0=0
 /*if isNotEmpty(schemaName) */ AND n.nspname IN /*schemaName*/('%') /*end*/
 /*if isNotEmpty(tableName) */ AND ti.relname IN /*tableName*/('%') /*end*/
 /*if isNotEmpty(indexName)*/ AND ci.relname IN /*indexName*/('%') /*end*/
ORDER BY n.nspname,ci.relname,a.attnum
