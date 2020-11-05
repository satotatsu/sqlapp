SELECT *
FROM ALL_TAB_PRIVS 
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
  AND PRIVILEGE='SELECT'