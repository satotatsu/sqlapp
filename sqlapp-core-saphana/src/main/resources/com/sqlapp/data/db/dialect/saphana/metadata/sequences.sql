SELECT s.*
  , (SELECT MAX(ms.CURRENT_VALUE) FROM M_SEQUENCES ms WHERE ms.SCHEMA_NAME = s.SCHEMA_NAME AND ms.SEQUENCE_NAME = s.SEQUENCE_NAME) AS CURRENT_VALUE
FROM SEQUENCES s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND s.SEQUENCE_NAME IN /*sequenceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME, SEQUENCE_NAME