SELECT *
FROM information_schema.sequences
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND sequence_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND sequence_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND sequence_name IN /*sequenceName*/('%')
  /*end*/
ORDER BY sequence_catalog, sequence_schema, sequence_name