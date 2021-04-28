SELECT
    DB_NAME() AS catalog_name
  , prv.value
  , pf.function_id
  , prv.parameter_id
  , t.name AS type_name
  , pp.max_length
  , pp.precision
  , pp.scale
  , pf.name AS partition_function_name
  , pf.create_date
  , pf.modify_date
  , pf.fanout
  , pf.boundary_value_on_right
FROM sys.partition_functions pf
INNER JOIN sys.partition_parameters pp
  ON (pf.function_id=pp.function_id) 
INNER JOIN sys.types t
  ON (pp.system_type_id = t.system_type_id)
INNER JOIN sys.partition_range_values prv
  ON (pp.parameter_id=prv.parameter_id 
  AND prv.function_id = pp.function_id)
WHERE 1=1
  /*if isNotEmpty(partitionFunctionName) */
  AND pf.name IN /*partitionFunctionName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY
  pf.function_id
  , prv.parameter_id
  , boundary_id

--1	int	4	10	0	myRangePF1	65536	4	0
--100	int	4	10	0	myRangePF1	65536	4	0
--1000	int	4	10	0	myRangePF1	65536	4	0
