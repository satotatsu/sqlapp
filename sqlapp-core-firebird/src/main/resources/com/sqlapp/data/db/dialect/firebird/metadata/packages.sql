SELECT
P.*
, CASE WHEN P.RDB$PACKAGE_BODY_SOURCE IS NULL THEN 0 ELSE 1 END AS HAS_BODY
FROM RDB$PACKAGES P
WHERE RDB$SYSTEM_FLAG=0
  /*if isNotEmpty(packageName) */
  AND RDB$PACKAGE_NAME IN /*packageName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND P.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$PACKAGE_NAME
