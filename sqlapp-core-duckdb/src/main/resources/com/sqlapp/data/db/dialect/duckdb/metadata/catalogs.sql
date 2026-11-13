SELECT
  d.database_name AS catalog_name
, d.path
, d.type AS database_type
, d.readonly
, d.comment AS remarks
FROM duckdb_databases() d
WHERE NOT d.internal
  /*if isNotEmpty(catalogName)*/
  AND d.database_name IN /*catalogName*/('%')
  /*end*/
ORDER BY d.database_name
