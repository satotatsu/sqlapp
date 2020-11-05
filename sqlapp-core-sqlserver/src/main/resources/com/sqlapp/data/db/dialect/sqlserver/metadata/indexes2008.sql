SELECT
    DB_NAME() AS catalog_name
  , SCHEMA_NAME(o.schema_id) AS schema_name
  , i.name AS index_name
  , o.name AS table_name
  , i.index_id
  , ic.key_ordinal
  , c.user_type_id
  , i.object_id
  , c.column_id
  , c.name AS column_name
  , i.type
  , is_unique
  , i.ignore_dup_key
  , i.is_primary_key
  , i.is_unique_constraint
  , i.is_unique
  , i.fill_factor
  , i.is_padded AS pad_index
  , i.is_disabled
  , i.allow_row_locks
  , i.allow_page_locks
  , ic.is_descending_key
  , ic.is_included_column
  , st.no_recompute AS statistics_norecompute
  , i.filter_definition
  , INDEXPROPERTY(o.object_id, i.name, 'IsAutoStatistics')
  AS auto_create_statistics
  , dsidx.Name as index_file_group_name
  , sit.tessellation_scheme
  , sit.bounding_box_xmax
  , sit.bounding_box_xmin
  , sit.bounding_box_ymax
  , sit.bounding_box_ymin
  , sit.cells_per_object
  , sit.level_1_grid_desc AS level_1_grid
  , sit.level_2_grid_desc AS level_2_grid
  , sit.level_3_grid_desc AS level_3_grid
  , sit.level_4_grid_desc AS level_4_grid
FROM sys.indexes i
INNER JOIN sys.objects o
  ON (i.object_id = o.object_id)
INNER JOIN sys.index_columns ic
  ON (i.index_id = ic.index_id
  AND i.object_id = ic.object_id)
INNER JOIN sys.columns c
  ON (ic.column_id = c.column_id
  AND ic.object_id = c.object_id)
LEFT OUTER JOIN sys.stats st
  ON (i.index_id=st.stats_id 
  AND i.object_id=st.object_id)
INNER JOIN sys.data_spaces dsidx 
  ON (i.data_space_id = dsidx.data_space_id)
LEFT OUTER JOIN sys.spatial_index_tessellations sit
  ON (i.object_id=sit.object_id
  AND i.index_id=sit.index_id)
WHERE i.type IN (1, 2, 3, 4)
  AND i.is_unique_constraint = 0 
  AND i.is_primary_key = 0 
--  AND objectproperty(i.object_id, 'IsMSShipped') <> 1 
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(o.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(indexName) */
  AND i.name IN /*indexName;type=NVARCHAR*/('%')
  /*end*/
  /*end*/
  /*if isNotEmpty(tableName) */
  AND o.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(o.schema_id), i.Name, ic.key_ordinal