SELECT gp.*
FROM GRANTED_PRIVILEGES gp
WHERE gp.OBJECT_TYPE IN ('FUNCTION', 'PROCEDURE')
  AND gp.PRIVILEGE = 'EXECUTE'
  AND gp.COLUMN_NAME IS NULL
  /*if isNotEmpty(schemaName)*/
  AND gp.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND gp.OBJECT_NAME IN /*objectName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gp.GRANTEE, gp.GRANTOR, gp.SCHEMA_NAME, gp.OBJECT_NAME
