SELECT
  index_name
, table_name
, column_name
, status
FROM __all_indexes__
WHERE 1=1
  /*if isNotEmpty(tableName) */
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND index_name IN /*indexName*/('%')
  /*end*/
