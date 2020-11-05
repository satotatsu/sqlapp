SELECT 
col.*
, c.COMMENT
, s.SEQUENCE_NAME
FROM V_CATALOG.VIEW_COLUMNS col
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (s.SEQUENCE_ID=c.OBJECT_ID)
LEFT OUTER JOIN V_CATALOG.SEQUENCES s
  ON (c.TABLE_ID=s.IDENTITY_TABLE_ID)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND col.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND col.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND col.COLUMN_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY col.TABLE_SCHEMA, col.TABLE_NAME, col.ORDINAL_POSITION