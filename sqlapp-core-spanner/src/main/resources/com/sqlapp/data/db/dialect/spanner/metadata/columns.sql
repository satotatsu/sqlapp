SELECT 
col.*
, col.TABLE_CATALOG AS `table_catalog`
, col.TABLE_SCHEMA AS `table_schema`
, col.TABLE_NAME AS `table_name`
, col.COLUMN_NAME AS `column_name`
, col.IS_NULLABLE AS `is_nullable`
, col.SPANNER_TYPE AS `spanner_type`
, col.COLUMN_DEFAULT AS `column_default`
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.COLUMN_OPTIONS opt
    WHERE opt.TABLE_SCHEMA=col.TABLE_SCHEMA
      AND opt.TABLE_NAME=col.TABLE_NAME
      AND opt.COLUMN_NAME=col.COLUMN_NAME
      AND opt.OPTION_NAME='allow_commit_timestamp'
  ) AS `allow_commit_timestamp`
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.COLUMN_OPTIONS opt
    WHERE opt.TABLE_SCHEMA=col.TABLE_SCHEMA
      AND opt.TABLE_NAME=col.TABLE_NAME
      AND opt.COLUMN_NAME=col.COLUMN_NAME
      AND opt.OPTION_NAME='vector_length'
  ) AS `vector_length`
, (
    SELECT opt.OPTION_VALUE
    FROM INFORMATION_SCHEMA.COLUMN_OPTIONS opt
    WHERE opt.TABLE_SCHEMA=col.TABLE_SCHEMA
      AND opt.TABLE_NAME=col.TABLE_NAME
      AND opt.COLUMN_NAME=col.COLUMN_NAME
      AND opt.OPTION_NAME='locality_group'
  ) AS `locality_group`
FROM INFORMATION_SCHEMA.COLUMNS col
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
