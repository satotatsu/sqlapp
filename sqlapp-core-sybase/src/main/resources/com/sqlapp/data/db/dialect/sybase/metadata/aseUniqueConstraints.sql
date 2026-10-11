SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, so.name AS table_name
, si.name AS index_name
, index_col(so.name, si.indid, n.number, su.uid) AS column_name
, n.number AS keyno
, CASE WHEN (si.status & 2048) = 2048 THEN 1 ELSE 0 END AS is_primary_key
, CASE si.indid WHEN 1 THEN 1 ELSE 2 END AS type
, 0 AS is_disabled
, si.fill_factor AS fill_factor
, NULL AS pad_index
, NULL AS allow_row_locks
, NULL AS allow_page_locks
, NULL AS auto_create_statistics
, NULL AS index_file_group_name
, 0 AS is_descending_key
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN sysindexes si
  ON (so.id = si.id AND si.indid > 0 AND si.indid < 250)
INNER JOIN master.dbo.spt_values n
  ON (n.type = 'P' AND n.number BETWEEN 1 AND si.keycnt)
WHERE so.type = 'U'
  AND ((si.status & 2048) = 2048 OR (si.status & 2) = 2)
  AND index_col(so.name, si.indid, n.number, su.uid) IS NOT NULL
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(constraintName) */
  AND si.name IN /*constraintName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=VARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name, si.name, n.number
