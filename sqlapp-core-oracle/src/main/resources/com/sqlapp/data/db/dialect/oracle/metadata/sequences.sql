SELECT *
FROM ALL_SEQUENCES
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND SEQUENCE_OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND SEQUENCE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY SEQUENCE_OWNER, SEQUENCE_NAME