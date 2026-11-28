SELECT
    DB_NAME() AS catalog_name
  , SCHEMA_NAME(o.schema_id) AS schema_name
  , i.name AS index_name
  , o.name AS table_name
  , i.index_id
  , ps.name AS partition_scheme
  , ic.partition_ordinal
  , ic.key_ordinal
  , c.user_type_id
  , i.object_id
  , c.column_id
  , c.name AS column_name
  , i.type
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
  , pt.data_compression_desc AS data_compression
  , i.compression_delay
  , ic.column_store_order_ordinal
  , CASE WHEN i.type IN (8, 9) AND i.data_space_id = base.data_space_id THEN NULL ELSE dsidx.name END AS file_group_name
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
  , i.optimize_for_sequential_key
  , vi.vector_index_type
  , vi.distance_metric
  , ji.optimize_for_array_search
  , jp.json_index_paths
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
LEFT OUTER JOIN sys.data_spaces dsidx
  ON (i.data_space_id = dsidx.data_space_id)
LEFT OUTER JOIN sys.spatial_index_tessellations sit
  ON (i.object_id=sit.object_id
  AND i.index_id=sit.index_id)
LEFT JOIN sys.partition_schemes ps ON ps.data_space_id = i.data_space_id
LEFT OUTER JOIN sys.partitions pt
  ON (pt.object_id = i.object_id
  AND pt.index_id = i.index_id
  AND pt.partition_number = 1)
LEFT JOIN sys.indexes base ON base.object_id = i.object_id AND base.index_id IN (0, 1)
LEFT JOIN sys.vector_indexes vi ON vi.object_id = i.object_id AND vi.index_id = i.index_id
LEFT JOIN sys.json_indexes ji ON ji.object_id = i.object_id AND ji.index_id = i.index_id
LEFT JOIN (
  SELECT object_id, index_id,
    N'[' + STRING_AGG(N'"' + STRING_ESCAPE(CONVERT(nvarchar(max), path), 'json') + N'"', N',')
      WITHIN GROUP (ORDER BY path) + N']' AS json_index_paths
  FROM sys.json_index_paths
  GROUP BY object_id, index_id
) jp ON jp.object_id = i.object_id AND jp.index_id = i.index_id
WHERE i.type IN (1, 2, 3, 4, 5, 6, 8, 9)
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
ORDER BY SCHEMA_NAME(o.schema_id), o.name, i.name, ic.column_store_order_ordinal, ic.key_ordinal, ic.index_column_id
