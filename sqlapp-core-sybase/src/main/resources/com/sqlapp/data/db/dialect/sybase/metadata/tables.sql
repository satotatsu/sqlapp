SELECT
    DB_NAME() AS catalog_name
  , su.name AS schema_name
  , so.name AS table_name
  , so.id AS table_id
  , so.crdate AS create_date
  , OBJECTPROPERTY(so.id, 'TableTextInRowLimit') AS text_in_row_limit
  , OBJECTPROPERTY(so.ID, 'TableHasClustIndex') AS has_clustered_index 
  , sfg.groupname AS file_group_name
  , COALESCE(sfg2.groupname, '') AS lob_file_group_name
FROM sysobjects so
INNER JOIN sysusers su
  ON (so.uid = su.uid)
LEFT OUTER JOIN sysindexes si
  ON (so.id = si.id AND si.indid < 2) --0 = ヒープ、 1 = クラスタ化インデックス
LEFT OUTER JOIN sysfilegroups sfg 
  ON (si.groupid = sfg.groupid) 
LEFT OUTER JOIN sysindexes si2 
  ON (so.id = si2.id AND si2.indid = 255) --text、ntext、または image 型を含む場合
LEFT OUTER JOIN sysfilegroups sfg2
  ON (si2.groupid = sfg2.groupid)
WHERE 1=1
--  AND type = 'U'
  /*if isNotEmpty(schemaName) */
  AND su.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND so.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY su.name, so.name
