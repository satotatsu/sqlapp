SELECT 
    s.name AS schema_name
    , t.name AS table_name
	, MAX(p.partition_number) AS partition_number
    , MAX(p.rows) AS rows
    , COALESCE(SUM(CAST(a.total_pages AS bigint)), 0) * 8 * 1024 AS total_space
    , COALESCE(SUM(CAST(a.used_pages AS bigint)), 0) * 8 * 1024 AS used_space
FROM sys.tables t
INNER JOIN sys.indexes i
  ON (t.object_id = i.object_id)
INNER JOIN sys.partitions p
  ON (i.object_id = p.object_id AND i.index_id = p.index_id)
LEFT JOIN sys.allocation_units a
  ON (a.container_id = CASE WHEN a.type IN (1, 3) THEN p.hobt_id ELSE p.partition_id END)
LEFT OUTER JOIN sys.schemas s 
  ON (t.schema_id = s.schema_id)
WHERE i.[type] <= 1
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