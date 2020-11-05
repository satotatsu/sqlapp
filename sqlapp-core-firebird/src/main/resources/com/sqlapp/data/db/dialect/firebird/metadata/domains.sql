SELECT
f.*
FROM RDB$FIELDS f
WHERE 1=1
  /*if readerOptions.excludeSystemObjects */
  AND f.RDB$SYSTEM_FLAG=0
  /*end*/
  /*if isNotEmpty(domainName) */
  AND f.RDB$FIELD_NAME IN /*domainName*/('%')
  /*end*/
ORDER BY RDB$FIELD_NAME