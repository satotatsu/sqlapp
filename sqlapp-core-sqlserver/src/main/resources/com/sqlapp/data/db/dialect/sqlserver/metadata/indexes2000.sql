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
, INDEXPROPERTY(SI.id, SI.name, 'IsPadIndex') AS pad_index
, CASE INDEXPROPERTY(SI.id, SI.name, 'IsRowLockDisallowed') WHEN 1 THEN 0 ELSE 1 END
  AS allow_row_locks
, CASE INDEXPROPERTY(SI.id, SI.name, 'IsPageLockDisallowed') WHEN 1 THEN 0 ELSE 1 END
  AS allow_page_locks
, CASE INDEXPROPERTY(SI.id, SI.name, 'IsAutoStatistics') WHEN 0 THEN 1 ELSE 0 END
  AS statistics_norecompute
, INDEXPROPERTY(SI.id, SI.name, 'IsAutoStatistics')
  AS auto_create_statistics
, INDEXPROPERTY(SI.id, SI.name, 'IndexFillFactor') AS fill_factor
, INDEXKEY_PROPERTY(SI.id, SI.indid, c.colid, 'IsDescending') AS is_descending
, sfg.groupname AS index_file_group_name
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN sysindexes si
  ON (so.id=si.id AND si.indid < 250)
LEFT OUTER JOIN sysfilegroups sfg 
  ON (si.groupid = sfg.groupid)
INNER JOIN sysindexkeys ik
  ON (si.id=ik.id
  AND si.indid=ik.indid)
INNER JOIN syscolumns c
  ON (ik.id=c.id
  AND ik.colid=c.colid)
WHERE 1=1
--  AND so.type='U'
  AND si.status<2048
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND si.name IN /*indexName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY su.name, si.name, ik.keyno
