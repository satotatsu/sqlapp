SELECT 
PT.OWNER AS schema_name
, PT.*
, TP.*
FROM ALL_PART_TABLES PT
INNER JOIN ALL_TAB_PARTITIONS TP
  ON (PT.OWNER=TP.TABLE_OWNER
  AND PT.TABLE_NAME=TP.TABLE_NAME)
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND PT.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND PT.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY PT.OWNER, PT.TABLE_NAME, TP.PARTITION_POSITION