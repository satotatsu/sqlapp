SELECT
p.table_schema AS schema_name
, p.*
FROM information_schema.partitions p
WHERE partition_name IS NOT NULL 
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
ORDER BY table_schema, table_name, partition_ordinal_position, subpartition_ordinal_position
