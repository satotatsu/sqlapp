SELECT 
    p.*
FROM FUNCTION_PARAMETERS p
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND p.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.FUNCTION_NAME IN /*routineName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY p.SCHEMA_NAME, p.FUNCTION_NAME, p.POSITION