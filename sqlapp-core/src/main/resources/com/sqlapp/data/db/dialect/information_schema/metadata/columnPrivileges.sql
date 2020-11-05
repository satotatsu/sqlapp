SELECT
*
FROM information_schema.column_privileges
WHERE 1=1
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND column_name IN /*columnName*/('%')
  /*end*/
ORDER BY grantor, grantee, table_catalog, table_schema, table_name, column_name
