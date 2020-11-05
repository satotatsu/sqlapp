SELECT 
  s.*
FROM information_schema.sequences s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND sequence_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND sequence_name IN /*sequenceName*/('%')
  /*end*/
ORDER BY sequence_schema, sequence_name