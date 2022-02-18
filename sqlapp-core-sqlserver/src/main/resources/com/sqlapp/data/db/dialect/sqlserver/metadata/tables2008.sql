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
	, large_value_types_out_of_row
	, COALESCE(objectproperty(T.object_id, 'TableHasVarDecimalStorageFormat'),0) AS has_var_decimal
	, DSIDX.name AS file_group_name
	, COALESCE(lob.Name,'') AS lob_file_group_name
	, COALESCE(filestr.Name,'') AS stream_file_group_name
	, COALESCE(CTT.is_track_columns_updated_on,0) AS is_track_columns_updated_on
	, COALESCE(CTT.object_id,0) AS has_change_tracking
	, CAST(ex.value AS NVARCHAR(4000)) AS remark
	, ps.name AS partition_scheme
	, p.data_compression
	, p.data_compression_desc
FROM sys.tables T
INNER JOIN sys.schemas s
  ON (s.schema_id = T.schema_id)
LEFT OUTER JOIN sys.indexes IDX
  ON (IDX.object_id = T.object_id and IDX.index_id < 2)
LEFT OUTER JOIN sys.data_spaces AS DSIDX
  ON (DSIDX.data_space_id = IDX.data_space_id)
LEFT OUTER JOIN sys.data_spaces AS lob
  ON (lob.data_space_id = T.lob_data_space_id)
LEFT OUTER JOIN sys.data_spaces AS filestr
  ON (filestr.data_space_id = T.filestream_data_space_id)
INNER JOIN sys.partitions p
  ON (IDX.object_id = p.OBJECT_ID AND IDX.index_id = p.index_id)
LEFT OUTER JOIN sys.change_tracking_tables CTT
  ON (CTT.object_id = T.object_id)
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
ORDER BY S.name, T.name