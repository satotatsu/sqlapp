SELECT
  c.*
FROM information_schema.column_privileges c
WHERE 1=1
  AND c.grantee NOT IN ('_SYSTEM', 'DBA', 'CREATE_SCHEMA', 'CHANGE_AUTHORIZATION', 'SCRIPT_OPS')
  AND c.privilege_type IN ('SELECT', 'INSERT', 'UPDATE', 'REFERENCES')
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
