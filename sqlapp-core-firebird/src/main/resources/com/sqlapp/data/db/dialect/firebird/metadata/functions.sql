SELECT
F.*
FROM RDB$FUNCTIONS F
WHERE 1=1
  /*if isNotEmpty(functionName) */
  AND RDB$SYSTEM_FLAG=0
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND RDB$FUNCTION_NAME NOT LIKE 'RDB$%'
  /*end*/
ORDER BY RDB$FUNCTION_NAME
