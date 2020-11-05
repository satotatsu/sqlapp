SELECT 
f.*
FROM V_CATALOG.USER_FUNCTIONS f
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND f.SCHEMA_NAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName)*/
  AND f.FUNCTION_NAME IN /*functionName*/('%')
  /*end*/
ORDER BY f.SCHEMA_NAME, f.FUNCTION_NAME