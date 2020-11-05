SELECT
  DB_NAME() AS catalog_name
, su.name AS schema_name
, so.name AS table_name
, si.name AS index_name
, c.name AS column_name
, ik.keyno
, con.status
, (con.status&1) AS is_primary_key
, CASE si.indid
  WHEN 0 THEN 0     --0 = ヒープ
  WHEN 1 THEN 1     --1 = クラスタ化インデックス
  ELSE 2            --1 >= 非クラスタ化インデックス
  END
  AS type
, INDEXPROPERTY(si.id, si.name, 'IsPadIndex') AS pad_index
, CASE INDEXPROPERTY(si.id, si.name, 'IsRowLockDisallowed') WHEN 1 THEN 0 ELSE 1 END
  AS allow_row_locks
, CASE INDEXPROPERTY(si.id, si.name, 'IsPageLockDisallowed') WHEN 1 THEN 0 ELSE 1 END
  AS allow_page_locks
, INDEXPROPERTY(si.id, si.name, 'IsAutoStatistics')
  AS auto_create_statistics
, INDEXPROPERTY(si.id, si.name, 'IndexFillFactor') AS fill_factor
, INDEXKEY_PROPERTY(si.id, si.indid, c.colid, 'IsDescending') AS is_descending_key
, OBJECTPROPERTY(con.constid, 'CnstIsDisabled')  AS is_disabled --制約の無効化(1 or 0)
, sfg.groupname AS index_file_group_name
FROM sysconstraints con
INNER JOIN sysobjects so
  ON (con.id=so.id)
INNER JOIN sysusers su
  ON (so.uid = su.uid)
INNER JOIN sysindexes si
  ON (con.id=si.id 
  AND OBJECT_NAME(con.constid)=si.name
  AND si.indid < 250)
LEFT OUTER JOIN sysfilegroups sfg 
  ON (si.groupid = sfg.groupid)
INNER JOIN sysindexkeys ik
  ON (si.id=ik.id
  AND si.indid=ik.indid)
INNER JOIN syscolumns c
  ON (ik.id=c.id
  AND ik.colid=c.colid)
WHERE ((con.status&1)=1 OR (con.status&2)=2)
--  AND so.type='U'
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(constraintName) */
  AND si.name IN /*constraintName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY su.name, si.name, ik.keyno
