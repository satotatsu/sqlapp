SELECT
  s.*
FROM SCHEMAS s
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME
