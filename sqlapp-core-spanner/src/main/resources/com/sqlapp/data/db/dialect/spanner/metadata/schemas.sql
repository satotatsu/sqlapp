SELECT s.SCHEMA_NAME AS schema_name
FROM INFORMATION_SCHEMA.SCHEMATA s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.SCHEMA_NAME IN /*schemaName*/('%')
  /*end*/
ORDER BY s.SCHEMA_NAME
