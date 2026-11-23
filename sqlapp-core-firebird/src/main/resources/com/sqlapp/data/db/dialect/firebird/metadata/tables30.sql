SELECT
  R.*
, R.RDB$RELATION_TYPE AS RELATION_TYPE
, S.*
, RS.*
FROM RDB$RELATIONS R
-- MON$TABLE_STATS has a row per statistics group (database, attachment,
-- transaction, statement, call); only the database-wide row (0) keeps one
-- row per table.
LEFT OUTER JOIN MON$TABLE_STATS S
  ON (R.RDB$RELATION_NAME=S.MON$TABLE_NAME
  AND S.MON$STAT_GROUP=0)
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
