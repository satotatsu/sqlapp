SELECT
  k.table_catalog
, k.table_schema
, k.table_name
, k.constraint_name
, k.constraint_type
, k.column_name
, k.ordinal_position
FROM (
  SELECT
    c.database_name AS table_catalog
  , c.schema_name AS table_schema
  , c.table_name
  , c.constraint_index
  , c.constraint_name
  , c.constraint_type
  , UNNEST(c.constraint_column_names) AS column_name
  , UNNEST(range(1, len(c.constraint_column_names) + 1)) AS ordinal_position
  FROM duckdb_constraints() c
  WHERE c.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
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
) k
ORDER BY k.table_catalog, k.table_schema, k.table_name, k.constraint_index, k.ordinal_position
