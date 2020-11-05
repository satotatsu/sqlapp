SELECT
  tc.*
FROM all_tab_columns tc
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND tc.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND tc.COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY tc.OWNER, tc.TABLE_NAME, tc.COLUMN_ID
