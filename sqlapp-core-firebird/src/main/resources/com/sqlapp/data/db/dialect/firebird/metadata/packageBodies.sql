SELECT
P.*
FROM RDB$PACKAGES P
WHERE RDB$SYSTEM_FLAG=0
  AND RDB$PACKAGE_BODY_SOURCE IS NOT NULL
  /*if isNotEmpty(packageName) */
  AND RDB$PACKAGE_NAME IN /*packageName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND P.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$PACKAGE_NAME
