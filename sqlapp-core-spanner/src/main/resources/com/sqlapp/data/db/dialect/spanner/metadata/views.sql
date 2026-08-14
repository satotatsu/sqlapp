SELECT
  v.TABLE_CATALOG AS `table_catalog`
, v.TABLE_SCHEMA AS `table_schema`
, v.TABLE_NAME AS `table_name`
, v.VIEW_DEFINITION AS `view_definition`
, v.SECURITY_TYPE AS `security_type`
FROM INFORMATION_SCHEMA.VIEWS v
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND v.TABLE_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND v.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND v.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY v.TABLE_CATALOG, v.TABLE_SCHEMA, v.TABLE_NAME
