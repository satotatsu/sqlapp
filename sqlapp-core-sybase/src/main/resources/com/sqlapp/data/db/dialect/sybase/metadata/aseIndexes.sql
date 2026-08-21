SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, so.name AS table_name
, si.name AS index_name
, index_col(so.name, si.indid, n.number, su.uid) AS column_name
, n.number AS keyno
, CASE WHEN (si.status & 2) = 2 THEN 1 ELSE 0 END AS is_unique
, CASE si.indid WHEN 1 THEN 1 ELSE 2 END AS type
, NULL AS fill_factor
, NULL AS pad_index
, NULL AS allow_row_locks
, NULL AS allow_page_locks
, NULL AS auto_create_statistics
, NULL AS index_file_group_name
, CASE WHEN index_colorder(so.name, si.indid, n.number, su.uid) = 'DESC'
    THEN 1 ELSE 0 END AS is_descending
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN sysindexes si
  ON (so.id = si.id AND si.indid > 0 AND si.indid < 250)
INNER JOIN master.dbo.spt_values n
  ON (n.type = 'P' AND n.number BETWEEN 1 AND si.keycnt)
WHERE so.type = 'U'
  AND index_col(so.name, si.indid, n.number, su.uid) IS NOT NULL
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND si.name IN /*indexName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=VARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name, si.name, n.number
