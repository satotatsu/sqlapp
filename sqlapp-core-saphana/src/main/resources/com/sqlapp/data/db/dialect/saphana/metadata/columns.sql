SELECT
  tc.*
FROM TABLE_COLUMNS tc
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND tc.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND tc.COLUMN_NAME IN /*columnName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY tc.SCHEMA_NAME, tc.TABLE_NAME, tc.POSITION
