SELECT
  vc.*
FROM VIEW_COLUMNS vc
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND vc.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND vc.VIEW_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND vc.COLUMN_NAME IN /*columnName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY vc.SCHEMA_NAME, vc.VIEW_NAME, vc.POSITION
