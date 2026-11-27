SELECT
  c.database_name AS table_catalog
, c.schema_name AS table_schema
, c.table_name
, c.column_name
, c.column_index AS ordinal_position
, c.is_nullable
, c.column_default
, c.data_type
, c.numeric_precision
, c.numeric_scale
, c.comment AS remarks
, t.sql AS table_sql
FROM duckdb_columns() c
LEFT JOIN duckdb_tables() t
  ON  t.database_name = c.database_name
  AND t.schema_name = c.schema_name
  AND t.table_name = c.table_name
WHERE NOT c.internal
  AND c.database_name NOT IN ('system', 'temp')
  /*if isNotEmpty(catalogName)*/
  AND c.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND c.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.column_name IN /*columnName*/('%')
  /*end*/
ORDER BY c.database_name, c.schema_name, c.table_name, c.column_index
