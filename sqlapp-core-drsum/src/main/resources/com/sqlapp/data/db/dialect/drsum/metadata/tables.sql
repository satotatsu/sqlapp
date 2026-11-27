SELECT
  table_name
, column_name
, assortment
, comments
, view_connect
FROM __all_tables__
WHERE view_select IS NULL
  /*if isNotEmpty(tableName) */
  AND table_name IN /*tableName*/('%')
  /*end*/
