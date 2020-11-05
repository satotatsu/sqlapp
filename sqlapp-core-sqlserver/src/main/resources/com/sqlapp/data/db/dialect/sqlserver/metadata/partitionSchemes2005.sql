SELECT
    DB_NAME() AS catalog_name
  , ps.data_space_id
  , ps.name AS partition_scheme_name
  , ds.name AS file_group_name
  , f.name AS function_name 
  , ps.is_default
  , ps.type_desc
FROM sys.partition_schemes ps
INNER JOIN sys.partition_functions f 
  ON (ps.function_id = f.function_id)
INNER JOIN sys.destination_data_spaces dd
  ON (ps.data_space_id = dd.partition_scheme_id)
INNER JOIN sys.data_spaces ds 
  ON (dd.data_space_id = ds.data_space_id)
WHERE 1=1
  /*if isNotEmpty(partitionSchemeName) */
  AND ps.name IN /*partitionSchemeName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY ps.data_space_id, dd.destination_id

--65602	myRangePS1	FG1	myRangePF1
--65602	myRangePS1	FG2	myRangePF1
--65602	myRangePS1	FG3	myRangePF1
--65602	myRangePS1	FG4	myRangePF1