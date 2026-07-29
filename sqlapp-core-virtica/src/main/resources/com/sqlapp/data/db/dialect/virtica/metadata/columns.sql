SELECT 
col.*
, c.COMMENT
, s.SEQUENCE_NAME
, s.SESSION_CACHE_COUNT
, s.ALLOW_CYCLE
, s.INCREMENT_BY
, s.MINIMUM
, s.MAXIMUM
, s.CURRENT_VALUE
FROM V_CATALOG.COLUMNS col
LEFT OUTER JOIN V_CATALOG.SEQUENCES s
  ON (col.TABLE_ID=s.IDENTITY_TABLE_ID AND col.IS_IDENTITY)
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (col.COLUMN_ID=c.OBJECT_ID)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND col.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND col.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND col.COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY col.TABLE_SCHEMA, col.TABLE_NAME, col.ORDINAL_POSITION
