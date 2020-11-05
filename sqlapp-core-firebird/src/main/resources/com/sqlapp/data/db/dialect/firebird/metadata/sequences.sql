SELECT
G.*
FROM RDB$GENERATORS G
WHERE RDB$SYSTEM_FLAG=0
  /*if isNotEmpty(sequenceName) */
  AND RDB$GENERATOR_NAME IN /*sequenceName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND G.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$GENERATOR_NAME
