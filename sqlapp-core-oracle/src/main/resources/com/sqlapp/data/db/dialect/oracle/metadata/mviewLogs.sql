SELECT
  A.*
FROM ALL_MVIEW_LOGS A
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND A.LOG_OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(mvewLogName)*/
  AND A.MASTER IN /*mvewLogName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND A.LOG_TABLE IN /*tableName*/('%')
  /*end*/
ORDER BY A.LOG_OWNER, A.LOG_TABLE
