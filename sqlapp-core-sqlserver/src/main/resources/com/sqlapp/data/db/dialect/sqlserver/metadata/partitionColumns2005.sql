SELECT   
  DB_NAME() AS catalog_name
, s.name AS schema_name
, t.object_id
, t.name AS table_name
, c.name AS column_name
FROM sys.tables t
INNER JOIN sys.schemas s
  ON (t.schema_id = s.schema_id)
INNER JOIN sys.indexes i
  ON (t.object_id = i.object_id
    AND i.[type] <= 1 -- clustered index or a heap
	)
INNER JOIN sys.partition_schemes ps
  ON (ps.data_space_id = i.data_space_id)
INNER JOIN sys.index_columns ic
  ON (ic.object_id = i.object_id
    AND ic.index_id = i.index_id
    AND ic.partition_ordinal >= 1 -- because 0 = non-partitioning column
	)
INNER JOIN sys.columns c   
  ON (t.object_id = c.object_id
     AND
     ic.column_id = c.column_id)
WHERE 1=1
--  AND type = 'U'
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('schemaName')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND t.name IN /*tableName;type=NVARCHAR*/('tableName')
  /*end*/
