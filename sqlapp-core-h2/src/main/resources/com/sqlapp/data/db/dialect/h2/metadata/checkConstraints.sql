SELECT
*
FROM information_schema.constraints
WHERE constraint_type='CHECK'
  /*if isNotEmpty(catalogName)*/
  AND constraint_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND constraint_name IN /*constraintName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
ORDER BY constraint_catalog, constraint_schema, table_name, constraint_name