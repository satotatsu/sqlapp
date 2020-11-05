SELECT
   *
FROM information_schema.indexes
WHERE constraint_name IS NULL
  /*if isNotEmpty(catalogName)*/
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND index_name IN /*indexName*/('%')
  /*end*/
ORDER BY table_catalog, table_schema, table_name, index_name, ordinal_position