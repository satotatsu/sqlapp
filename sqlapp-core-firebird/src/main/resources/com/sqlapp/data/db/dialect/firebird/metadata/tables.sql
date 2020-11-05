SELECT
  R.*
FROM RDB$RELATIONS R
WHERE R.RDB$VIEW_BLR IS NULL
  /*if isNotEmpty(tableName) */
  AND R.RDB$RELATION_NAME IN /*tableName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND R.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY R.RDB$RELATION_NAME