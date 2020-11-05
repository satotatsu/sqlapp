SELECT
    DB_NAME() AS catalog_name
  , s.name AS schema_name
  , t.name AS table_name
  , fc.name AS full_text_catalog_name
  , 'idx_'+ t.name + '_' + fc.name AS index_name
  , i.name AS unique_index_name
  , fi.is_enabled
  , c.name as column_name
  , fl.name AS language_name
  , fi.change_tracking_state_desc AS change_tracking
  , fi.crawl_start_date
  , fi.crawl_end_date
  , fi.has_crawl_completed
FROM sys.fulltext_indexes fi
INNER JOIN sys.fulltext_catalogs fc
  ON (fi.fulltext_catalog_id = fc.fulltext_catalog_id)
INNER JOIN sys.indexes i
  ON (fi.unique_index_id = i.index_id
  AND fi.object_id = i.object_id)
INNER JOIN sys.tables t
  ON (fi.object_id = t.object_id) 
INNER JOIN sys.schemas s
  ON t.schema_id = s.schema_id 
INNER JOIN sys.fulltext_index_columns fic
  ON fi.object_id = fic.object_id 
INNER JOIN sys.columns c
  ON (fic.object_id = c.object_id 
  AND fic.column_id = c.column_id) 
INNER JOIN sys.fulltext_languages fl
    ON (fic.language_id = fl.lcid)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND 'idx_'+ t.name + '_' + fc.name IN /*indexName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND t.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, t.name, i.name
