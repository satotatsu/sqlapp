SELECT
f.*
FROM FUNCTIONS f
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND f.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND f.FUNCTION_NAME IN /*functionName;type=NVARCHAR*/('%')
  /*end*/
order by f.SCHEMA_NAME, f.FUNCTION_NAME