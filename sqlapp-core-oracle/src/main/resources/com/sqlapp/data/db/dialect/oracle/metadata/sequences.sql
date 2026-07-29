SELECT S.*
, CAST('N' AS VARCHAR2(1)) AS SCALE_FLAG
, CAST('N' AS VARCHAR2(1)) AS EXTEND_FLAG
, CAST('N' AS VARCHAR2(1)) AS SESSION_FLAG
FROM ALL_SEQUENCES S
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND SEQUENCE_OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND SEQUENCE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY SEQUENCE_OWNER, SEQUENCE_NAME
