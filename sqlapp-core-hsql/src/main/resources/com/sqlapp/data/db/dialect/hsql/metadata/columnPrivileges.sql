SELECT
  c.*
FROM information_schema.column_privileges c
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND c.table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND c.table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.column_name IN /*columnName*/('%')
  /*end*/
ORDER BY c.grantor, c.grantee, c.table_schema, c.table_name, c.column_name
