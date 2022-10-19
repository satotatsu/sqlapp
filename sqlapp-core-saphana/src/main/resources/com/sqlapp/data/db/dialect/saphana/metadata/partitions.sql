SELECT 
pt.*
, tp.*
FROM PARTITIONED_TABLES pt
LEFT OUTER JOIN TABLE_PARTITIONS tp
  ON(
  pt.SCHEMA_NAME=tp.SCHEMA_NAME
  AND
  pt.TABLE_NAME=tp.TABLE_NAME
  )
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND pt.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND pt.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY 
    pt.SCHEMA_NAME, pt.TABLE_NAME, tp.LEVEL_1_PARTITION, tp.LEVEL_2_PARTITION
