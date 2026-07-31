SELECT V.*
  , C.COMMENT AS REMARKS
FROM V_CATALOG.VIEWS v
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (v.TABLE_ID=c.OBJECT_ID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND v.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND v.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY v.TABLE_SCHEMA, v.TABLE_NAME
