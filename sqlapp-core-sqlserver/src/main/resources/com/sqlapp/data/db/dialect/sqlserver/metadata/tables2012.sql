SELECT
      DB_NAME() AS catalog_name
    , t.type AS object_typelock_escalation_desc
	, t.name AS table_name
	, s.name AS schema_name
	, t.object_id AS table_id
	, t.create_date
	, t.modify_date
	, t.lock_escalation_desc AS lock_escalation
	, text_in_row_limit
	, p.data_compression_desc AS data_compression
	, idx.compression_delay
	, large_value_types_out_of_row
	, COALESCE(objectproperty(t.object_id, 'TableHasVarDecimalStorageFormat'),0) AS has_var_decimal
	, dsidx.name AS file_group_name
	, COALESCE(lob.Name,'') AS lob_file_group_name
	, COALESCE(filestr.Name,'') AS stream_file_group_name
	, COALESCE(ctt.is_track_columns_updated_on,0) AS is_track_columns_updated_on
	, COALESCE(ctt.object_id,0) AS has_change_tracking
	, CAST(ex.value AS NVARCHAR(4000)) AS remarks
	, ps.name AS partition_scheme
	, t.is_filetable
FROM sys.tables T
INNER JOIN sys.schemas s
  ON (s.schema_id = t.schema_id)
LEFT OUTER JOIN sys.indexes idx
  ON (idx.object_id = t.object_id and idx.index_id < 2)
LEFT OUTER JOIN sys.partitions p
  ON (idx.object_id = p.object_id
  AND idx.index_id=p.index_id
  AND p.partition_number=1)
LEFT OUTER JOIN sys.data_spaces AS dsidx
  ON (dsidx.data_space_id = idx.data_space_id)
LEFT OUTER JOIN sys.data_spaces AS lob
  ON (lob.data_space_id = t.lob_data_space_id)
LEFT OUTER JOIN sys.data_spaces AS filestr
  ON (filestr.data_space_id = t.filestream_data_space_id)
LEFT OUTER JOIN sys.change_tracking_tables ctt
  ON (ctt.object_id = t.object_id)
LEFT OUTER JOIN sys.extended_properties ex
  ON (t.object_id = ex.major_id
  AND ex.minor_id = 0)
LEFT OUTER JOIN sys.partition_schemes ps 
  ON (idx.data_space_id = ps.data_space_id)
WHERE 1=1
--  AND type = 'U'
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND t.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, t.name