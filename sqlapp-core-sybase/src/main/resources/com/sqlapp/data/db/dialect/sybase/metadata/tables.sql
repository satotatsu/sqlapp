SELECT
    DB_NAME() AS catalog_name
  , su.name AS schema_name
  , so.name AS table_name
  , so.id AS table_id
  , so.crdate AS create_date
  , NULL AS text_in_row_limit
  , NULL AS has_clustered_index
  , NULL AS file_group_name
  , NULL AS lob_file_group_name
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
WHERE 1=1
  AND so.type = 'U'
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=VARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name
