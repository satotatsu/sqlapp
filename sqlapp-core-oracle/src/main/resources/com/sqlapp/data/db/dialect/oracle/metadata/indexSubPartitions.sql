SELECT *
FROM ALL_IND_SUBPARTITIONS
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND INDEX_OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND INDEX_NAME IN /*indexName*/('%')
  /*end*/
  /*if isNotEmpty(partitionName)*/
  AND PARTITION_NAME IN /*partitionName*/('%')
  /*end*/
ORDER BY INDEX_OWNER, INDEX_NAME, SUBPARTITION_POSITION
