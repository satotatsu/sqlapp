SELECT
  cc.*
FROM all_col_comments cc
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND cc.OWNER IN /*schemaName*/('schema1')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cc.TABLE_NAME IN /*tableName*/('table1')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND cc.COLUMN_NAME IN /*columnName*/('COLA')
  /*end*/
ORDER BY cc.OWNER, cc.TABLE_NAME, cc.COLUMN_NAME
