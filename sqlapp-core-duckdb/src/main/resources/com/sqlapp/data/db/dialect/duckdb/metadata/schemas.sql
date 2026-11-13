SELECT
  s.database_name AS catalog_name
, s.schema_name
, s.comment AS remarks
FROM duckdb_schemas() s
WHERE s.database_name NOT IN ('system', 'temp')
  AND s.schema_name NOT IN ('information_schema', 'pg_catalog')
  /*if isNotEmpty(catalogName)*/
  AND s.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND s.schema_name IN /*schemaName*/('%')
  /*end*/
ORDER BY s.database_name, s.schema_name
