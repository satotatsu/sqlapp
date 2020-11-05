SELECT t.*
, c.COMMENT
FROM V_CATALOG.TABLES t
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (t.TABLE_ID=c.OBJECT_ID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
