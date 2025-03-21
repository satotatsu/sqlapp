SELECT
*
FROM information_schema.tables
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
ORDER BY table_catalog, table_schema, table_name
