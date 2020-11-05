SELECT 
  DB_NAME() AS catalog_name
, sty.name AS type_name
, SCHEMA_NAME(s.schema_id) AS schema_name
, s.name AS sequence_name
, cast(s.start_value AS Decimal(38)) AS start_value
, cast(s.increment AS Decimal(38)) AS increment
, cast(s.minimum_value AS Decimal(38)) AS minimum_value
, cast(s.maximum_value AS Decimal(38)) AS maximum_value
, cast(s.current_value AS Decimal(38)) AS current_value
, s.is_cached
, s.cache_size
, s.is_cycling
, s.modify_date
, s.create_date
, s.precision
, s.scale
FROM sys.sequences s
INNER JOIN sys.types sty
  ON (s.system_type_id = sty.system_type_id
  AND s.user_type_id = sty.user_type_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(s.schema_id) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND s.name IN /*sequenceName*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(s.schema_id), s.name