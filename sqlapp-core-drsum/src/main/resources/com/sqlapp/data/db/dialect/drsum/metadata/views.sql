SELECT
  table_name
, column_name
, assortment
, comments
, view_select
FROM __all_tables__
WHERE view_select IS NOT NULL
  /*if isNotEmpty(tableName) */
  AND table_name IN /*tableName*/('%')
  /*end*/
