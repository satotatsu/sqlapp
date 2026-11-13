SELECT
  t.database_name AS table_catalog
, t.schema_name AS table_schema
, t.table_name
, t.comment AS remarks
, t.estimated_size
, t.sql AS table_sql
FROM duckdb_tables() t
WHERE NOT t.internal
  AND t.database_name NOT IN ('system', 'temp')
  /*if isNotEmpty(catalogName)*/
  AND t.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.table_name IN /*tableName*/('%')
  /*end*/
ORDER BY t.database_name, t.schema_name, t.table_name
