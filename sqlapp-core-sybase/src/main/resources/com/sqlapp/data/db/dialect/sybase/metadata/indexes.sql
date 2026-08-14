SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, so.name AS table_name
, si.name AS index_name
, c.name AS column_name
, ik.keyno
, si.status
, CASE
  WHEN (si.status & 2048)=2048 THEN 1 --PRIMARY KEY
  WHEN (si.status & 4096)=4096 THEN 0 --UNIQUE
  ELSE 0
  END AS is_primary_key
, (CASE WHEN (si.status & 2)=2 THEN 1 else 0 END) is_unique
, CASE si.indid
  WHEN 0 THEN 0     --0 = ヒープ
  WHEN 1 THEN 1     --1 = クラスタ化インデックス
  ELSE 2            --1 >= 非クラスタ化インデックス
  END
  AS type
, NULL AS pad_index
, NULL AS allow_row_locks
, NULL AS allow_page_locks
, NULL AS auto_create_statistics
, NULL AS fill_factor
, 0 AS is_descending
, NULL AS index_file_group_name
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN sysindexes si
  ON (so.id=si.id AND si.indid < 250)
INNER JOIN sysindexkeys ik
  ON (si.id=ik.id
  AND si.indid=ik.indid)
INNER JOIN syscolumns c
  ON (ik.id=c.id
  AND ik.colid=c.colid)
WHERE 1=1
  AND so.type='U'
  AND si.indid > 0
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND si.name IN /*indexName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=VARCHAR*/('%')
  /*end*/
ORDER BY su.name, si.name, ik.keyno
