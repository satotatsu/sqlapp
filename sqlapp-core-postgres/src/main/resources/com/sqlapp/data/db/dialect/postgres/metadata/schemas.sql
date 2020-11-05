SELECT
  s.*
FROM information_schema.schemata s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND schema_name IN /*schemaName*/('%')
  /*end*/
ORDER BY catalog_name, schema_name
