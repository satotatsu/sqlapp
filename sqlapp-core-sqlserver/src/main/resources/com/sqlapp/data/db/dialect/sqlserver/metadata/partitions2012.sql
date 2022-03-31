SELECT 
    s.name AS schema_name
    , t.name AS table_name
	, MAX(p.partition_number) AS partition_number
    , MAX(p.rows) AS rows
    , MAX(data_compression_desc) AS data_compression_desc
    , SUM(a.total_pages) * 8 * 1024 AS total_space
    , SUM(a.used_pages) * 8 * 1024 AS used_space
FROM sys.tables t
INNER JOIN sys.indexes i
  ON (t.object_id = i.object_id)
INNER JOIN sys.partitions p
  ON (i.object_id = p.object_id AND i.index_id = p.index_id)
INNER JOIN sys.allocation_units a 
  ON (p.partition_id = a.container_id)
LEFT OUTER JOIN sys.schemas s 
  ON (t.schema_id = s.schema_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND t.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
GROUP BY 
    t.name, s.name, p.partition_number
ORDER BY 
    t.name, s.name, p.partition_number