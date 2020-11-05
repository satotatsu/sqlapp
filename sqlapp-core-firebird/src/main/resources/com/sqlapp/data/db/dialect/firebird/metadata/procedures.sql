SELECT
P.*
FROM RDB$PROCEDURES P
WHERE RDB$SYSTEM_FLAG=0
  /*if isNotEmpty(procedureName) */
  AND RDB$PROCEDURE_NAME IN /*procedureName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND P.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$PROCEDURE_NAME
