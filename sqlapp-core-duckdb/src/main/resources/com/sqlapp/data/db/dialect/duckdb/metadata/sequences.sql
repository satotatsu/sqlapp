SELECT
  s.database_name AS sequence_catalog
, s.schema_name AS sequence_schema
, s.sequence_name
, s.start_value
, s.min_value
, s.max_value
, s.increment_by
, s.cycle
, s.last_value
, s.comment AS remarks
FROM duckdb_sequences() s
WHERE s.database_name NOT IN ('system', 'temp')
  /*if isNotEmpty(catalogName)*/
  AND s.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND s.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND s.sequence_name IN /*sequenceName*/('%')
  /*end*/
ORDER BY s.database_name, s.schema_name, s.sequence_name
