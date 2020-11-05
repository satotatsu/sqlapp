SELECT
P.*
FROM RDB$PACKAGES P
WHERE RDB$SYSTEM_FLAG=0
  /*if isNotEmpty(packageName) */
  AND RDB$PACKAGE_NAME IN /*packageName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND P.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$PACKAGE_NAME
