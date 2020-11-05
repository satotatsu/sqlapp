SELECT
*
FROM information_schema.columns
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND column_name IN /*columnName*/('%')
  /*end*/
ORDER BY table_catalog, table_schema, table_name, ordinal_position