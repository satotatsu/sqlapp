SELECT 
      DB_NAME() AS catalog_name
	, s.name AS schema_name
    , t.type AS object_type
	, t.name AS table_name
	, t.object_id AS table_id
	, t.create_date
	, t.modify_date
	, text_in_row_limit
	, large_value_types_out_of_row
	, COALESCE(objectproperty(T.object_id, 'TableHasVarDecimalStorageFormat'),0) AS has_var_decimal
	, idxs.name AS file_group_name
	, COALESCE(lobs.Name,'') AS lob_file_group_name
	, CAST(ex.value AS NVARCHAR(4000)) AS remark
	, ps.name AS partition_scheme
FROM sys.tables t
INNER JOIN sys.schemas s
  ON (t.schema_id = s.schema_id)
LEFT OUTER JOIN sys.indexes idx
  ON (t.object_id=idx.object_id AND idx.index_id < 2)
LEFT OUTER JOIN sys.data_spaces AS idxs
  ON (idx.data_space_id = idxs.data_space_id)
LEFT OUTER JOIN sys.data_spaces AS lobs
  ON (t.lob_data_space_id = lobs.data_space_id)
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
ORDER BY s.name, t.Name
