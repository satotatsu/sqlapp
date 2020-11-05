SELECT
  CP.GRANTEE
  , CP.OWNER AS TABLE_SCHEMA
  , CP.TABLE_NAME
  , CP.COLUMN_NAME
  , CP.GRANTOR
  , CP.PRIVILEGE
  , CP.GRANTABLE
FROM DBA_COL_PRIVS CP
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND CP.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND CP.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND CP.COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY CP.GRANTOR, CP.GRANTEE, CP.OWNER, CP.TABLE_NAME, CP.COLUMN_NAME
