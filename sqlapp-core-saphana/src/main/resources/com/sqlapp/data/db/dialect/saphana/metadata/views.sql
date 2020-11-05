SELECT
  v.*
FROM VIEWS v
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND v.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND v.VIEW_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY v.SCHEMA_NAME, v.VIEW_NAME