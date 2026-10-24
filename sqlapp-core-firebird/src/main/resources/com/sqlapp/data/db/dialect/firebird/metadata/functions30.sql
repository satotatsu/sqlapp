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
  AND F.RDB$PACKAGE_NAME IS NULL
ORDER BY RDB$FUNCTION_NAME
