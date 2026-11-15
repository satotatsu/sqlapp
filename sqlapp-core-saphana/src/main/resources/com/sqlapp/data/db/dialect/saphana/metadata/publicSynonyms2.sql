SELECT S.*,
  CASE WHEN S.OBJECT_DATABASE = CURRENT_DATABASE() THEN NULL ELSE S.OBJECT_DATABASE END AS TARGET_DATABASE
FROM SYNONYMS S
WHERE S.SCHEMA_NAME = 'PUBLIC'
  /*if isNotEmpty(synonymName)*/
  AND S.SYNONYM_NAME IN /*synonymName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND S.OBJECT_SCHEMA IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isEmpty(schemaName)*/
  AND S.OBJECT_SCHEMA <> 'SYS'
  AND LEFT(S.OBJECT_SCHEMA, 5) <> '_SYS_'
  /*end*/
ORDER BY S.SYNONYM_NAME
