SELECT 
s.*
, c.COMMENT
FROM V_CATALOG.SEQUENCES s
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (s.SEQUENCE_ID=c.OBJECT_ID)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.SEQUENCE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND s.SEQUENCE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY s.SEQUENCE_SCHEMA, s.SEQUENCE_NAME