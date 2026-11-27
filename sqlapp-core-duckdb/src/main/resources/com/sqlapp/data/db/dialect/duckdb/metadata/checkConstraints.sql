SELECT
  c.database_name AS table_catalog
, c.schema_name AS table_schema
, c.table_name
, c.constraint_name
, c.expression AS check_expression
FROM duckdb_constraints() c
WHERE c.constraint_type = 'CHECK'
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
  /*if isNotEmpty(constraintName)*/
  AND c.constraint_name IN /*constraintName*/('%')
  /*end*/
ORDER BY c.database_name, c.schema_name, c.table_name, c.constraint_index
