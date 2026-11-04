SELECT
  i.*
, i.TABLE_CATALOG AS `table_catalog`
, i.TABLE_SCHEMA AS `table_schema`
, i.TABLE_NAME AS `table_name`
, i.INDEX_NAME AS `index_name`
, i.INDEX_TYPE AS `index_type`
, i.IS_UNIQUE AS `is_unique`
, i.IS_NULL_FILTERED AS `is_null_filtered`
, cc.COLUMN_NAME AS `column_name`
, cc.ORDINAL_POSITION AS `ordinal_position`
, cc.COLUMN_ORDERING AS `column_ordering`
, cc.IS_NULLABLE AS `index_is_nullable`
, bc.IS_NULLABLE AS `base_is_nullable`
FROM INFORMATION_SCHEMA.INDEXES i
INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS cc
  ON (i.TABLE_CATALOG=cc.TABLE_CATALOG
  AND i.TABLE_SCHEMA=cc.TABLE_SCHEMA
  AND
  i.TABLE_NAME=cc.TABLE_NAME
  AND
  i.INDEX_NAME=cc.INDEX_NAME
  )
LEFT JOIN INFORMATION_SCHEMA.COLUMNS bc
  ON bc.TABLE_CATALOG=cc.TABLE_CATALOG
 AND bc.TABLE_SCHEMA=cc.TABLE_SCHEMA
 AND bc.TABLE_NAME=cc.TABLE_NAME
 AND bc.COLUMN_NAME=cc.COLUMN_NAME
WHERE 1=1
  AND i.INDEX_TYPE IN ('INDEX', 'SEARCH', 'VECTOR')
  /*if isNotEmpty(schemaName)*/
  AND i.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND i.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND i.INDEX_NAME IN /*indexName*/('%')
  /*end*/
ORDER BY i.TABLE_SCHEMA, i.TABLE_NAME, i.INDEX_NAME,
  cc.ORDINAL_POSITION
