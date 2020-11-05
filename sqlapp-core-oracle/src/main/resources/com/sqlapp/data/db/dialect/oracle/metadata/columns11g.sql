SELECT
  tc.*
FROM all_tab_cols tc
WHERE 1=1
  /*if isNotEmpty(containsHiddenColumns)*/
  /*if !containsHiddenColumns*/
  AND tc.HIDDEN_COLUMN='NO'
  /*end*/
  /*if containsHiddenColumns*/
  AND tc.HIDDEN_COLUMN='YES'
  /*end*/
  /*end*/
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
