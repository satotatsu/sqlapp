SELECT
  t.TABLE_CATALOG AS `table_catalog`
, t.TABLE_SCHEMA AS `table_schema`
, t.TABLE_NAME AS `table_name`
, t.PARENT_TABLE_NAME AS `parent_table_name`
, t.ON_DELETE_ACTION AS `on_delete_action`
FROM INFORMATION_SCHEMA.TABLES t
WHERE 1=1
  AND t.TABLE_TYPE='BASE TABLE'
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
