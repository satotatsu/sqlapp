SELECT 
col.*
, col.TABLE_CATALOG AS `table_catalog`
, col.TABLE_SCHEMA AS `table_schema`
, col.TABLE_NAME AS `table_name`
, col.COLUMN_NAME AS `column_name`
, col.IS_NULLABLE AS `is_nullable`
, col.SPANNER_TYPE AS `spanner_type`
, col.COLUMN_DEFAULT AS `column_default`
, col.ON_UPDATE_EXPRESSION AS `on_update_expression`
, col.IS_GENERATED AS `is_generated`
, col.GENERATION_EXPRESSION AS `generation_expression`
, col.IS_STORED AS `is_stored`
, col.IS_HIDDEN AS `is_hidden`
, col.IS_IDENTITY AS `is_identity`
, col.IDENTITY_GENERATION AS `identity_generation`
, col.IDENTITY_START_WITH_COUNTER AS `identity_start_with_counter`
, col.IDENTITY_KIND AS `identity_kind`
, col.IDENTITY_SKIP_RANGE_MIN AS `identity_skip_range_min`
, col.IDENTITY_SKIP_RANGE_MAX AS `identity_skip_range_max`
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
