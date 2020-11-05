SELECT
  t.*
FROM TABLES t
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND t.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY t.SCHEMA_NAME, t.TABLE_NAME

