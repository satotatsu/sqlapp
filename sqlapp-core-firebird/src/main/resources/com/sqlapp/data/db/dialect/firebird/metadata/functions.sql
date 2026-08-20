SELECT
F.*
FROM RDB$FUNCTIONS F
WHERE 1=1
  /*if isNotEmpty(functionName) */
  AND RDB$FUNCTION_NAME IN /*functionName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND RDB$FUNCTION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$FUNCTION_NAME
