SELECT *
FROM ALL_TAB_SUBPARTITIONS
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND TABLE_OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(partitionName)*/
  AND PARTITION_NAME IN /*partitionName*/('%')
  /*end*/
ORDER BY TABLE_OWNER, TABLE_NAME, SUBPARTITION_POSITION
