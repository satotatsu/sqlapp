SELECT
  table_name
, column_name
, column_type
, column_null
, column_unique
, column_precision
, column_scale
, comments
FROM __all_tables__
WHERE column_name IS NOT NULL
  /*if isNotEmpty(tableName) */
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND column_name IN /*columnName*/('%')
  /*end*/
