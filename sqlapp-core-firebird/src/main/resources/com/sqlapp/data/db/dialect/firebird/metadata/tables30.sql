SELECT
  R.*
, S.*
, RS.*
FROM RDB$RELATIONS R
LEFT OUTER JOIN MON$TABLE_STATS S
  ON (R.RDB$RELATION_NAME=S.MON$TABLE_NAME)
LEFT OUTER JOIN MON$RECORD_STATS RS
  ON (S.MON$RECORD_STAT_ID=RS.MON$STAT_ID)
WHERE R.RDB$VIEW_BLR IS NULL
  /*if isNotEmpty(tableName) */
  AND R.RDB$RELATION_NAME IN /*tableName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND R.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY R.RDB$RELATION_NAME