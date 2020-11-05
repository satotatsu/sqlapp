SELECT
  A.*
FROM DBA_MVIEW_LOG_FILTER_COLS A
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND A.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND A.NAME IN /*tableName*/('%')
  /*end*/
