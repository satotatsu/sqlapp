SELECT
  S.*
FROM SYNONYMS  S
WHERE 0=0 
  /*if isNotEmpty(schemaName)*/
  AND S.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(synonymName)*/
  AND S.SYNONYM_NAME IN /*synonymName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY S.SCHEMA_NAME, S.SYNONYM_NAME