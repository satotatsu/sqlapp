SELECT
  table_name
, column_name
, column_unique
FROM __all_tables__
WHERE column_name IS NOT NULL
  AND view_select IS NULL
  AND column_unique IS NOT NULL
  /*if isNotEmpty(tableName) */
  AND table_name IN /*tableName*/('%')
  /*end*/
