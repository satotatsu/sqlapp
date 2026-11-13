SELECT
  v.database_name AS table_catalog
, v.schema_name AS table_schema
, v.view_name AS table_name
, v.comment AS remarks
, v.sql AS view_sql
FROM duckdb_views() v
WHERE NOT v.internal
  AND v.database_name NOT IN ('system', 'temp')
  /*if isNotEmpty(catalogName)*/
  AND v.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND v.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND v.view_name IN /*tableName*/('%')
  /*end*/
ORDER BY v.database_name, v.schema_name, v.view_name
