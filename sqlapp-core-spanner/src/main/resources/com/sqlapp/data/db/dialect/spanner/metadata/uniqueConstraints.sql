SELECT
  c.TABLE_CATALOG AS `table_catalog`
, c.TABLE_SCHEMA AS `table_schema`
, c.TABLE_NAME AS `table_name`
, c.INDEX_NAME AS `constraint_name`
, cc.COLUMN_NAME AS `column_name`
, cc.COLUMN_ORDERING AS `column_ordering`
FROM INFORMATION_SCHEMA.INDEX_COLUMNS c
INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS cc
  ON c.TABLE_CATALOG=cc.TABLE_CATALOG
 AND c.TABLE_SCHEMA=cc.TABLE_SCHEMA
 AND c.TABLE_NAME=cc.TABLE_NAME
 AND c.INDEX_NAME=cc.INDEX_NAME
WHERE 1=1
  AND c.INDEX_TYPE IN ('PRIMARY_KEY')
  /*if isNotEmpty(schemaName)*/
  AND c.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.INDEX_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.INDEX_NAME, cc.ORDINAL_POSITION
