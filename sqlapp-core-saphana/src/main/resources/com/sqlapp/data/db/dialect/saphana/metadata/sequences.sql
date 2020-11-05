SELECT s.*
FROM SEQUENCES s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND s.SEQUENCE_NAME IN /*sequenceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME, SEQUENCE_NAME