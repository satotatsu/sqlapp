SELECT
  *
FROM ALL_COL_PRIVS CP
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY GRANTOR, GRANTEE, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME
