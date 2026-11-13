SELECT
  i.database_name AS table_catalog
, i.schema_name AS table_schema
, i.table_name
, i.index_name
, i.is_unique
, i.comment AS remarks
, i.sql AS index_sql
FROM duckdb_indexes() i
WHERE i.database_name NOT IN ('system', 'temp')
  /*if isNotEmpty(catalogName)*/
  AND i.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND i.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND i.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND i.index_name IN /*indexName*/('%')
  /*end*/
ORDER BY i.database_name, i.schema_name, i.table_name, i.index_name
