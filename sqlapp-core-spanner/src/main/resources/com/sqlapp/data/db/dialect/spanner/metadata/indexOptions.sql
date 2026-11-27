SELECT
  opt.TABLE_CATALOG AS `table_catalog`
, opt.TABLE_SCHEMA AS `table_schema`
, opt.INDEX_NAME AS `index_name`
, opt.OPTION_NAME AS `option_name`
, opt.OPTION_VALUE AS `option_value`
FROM INFORMATION_SCHEMA.INDEX_OPTIONS opt
WHERE opt.OPTION_NAME IN ('distance_type', 'tree_depth', 'num_leaves', 'num_branches',
  'disable_search', 'sort_order_sharding', 'locality_group', 'columnar_policy')
  /*if isNotEmpty(schemaName)*/
  AND opt.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND opt.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND opt.INDEX_NAME IN /*indexName*/('%')
  /*end*/
ORDER BY opt.TABLE_SCHEMA, opt.TABLE_NAME, opt.INDEX_NAME
