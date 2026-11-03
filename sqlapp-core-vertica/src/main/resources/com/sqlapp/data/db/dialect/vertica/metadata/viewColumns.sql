SELECT 
col.*
, NULL AS TABLE_CATALOG
, TRUE AS IS_NULLABLE
, FALSE AS IS_IDENTITY
, NULL AS COLUMN_DEFAULT
, NULL AS COMMENT
FROM V_CATALOG.VIEW_COLUMNS col
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND col.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND col.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND col.COLUMN_NAME IN /*columnName*/('%')
  /*end*/
ORDER BY col.TABLE_SCHEMA, col.TABLE_NAME, col.ORDINAL_POSITION
