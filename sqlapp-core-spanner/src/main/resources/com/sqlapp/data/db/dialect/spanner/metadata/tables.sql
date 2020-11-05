SELECT t.*
FROM INFORMATION_SCHEMA.TABLES t
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
