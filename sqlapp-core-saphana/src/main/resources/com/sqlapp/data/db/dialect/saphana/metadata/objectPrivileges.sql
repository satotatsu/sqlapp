SELECT
  gp.*
FROM GRANTED_PRIVILEGES gp
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND gp.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(objectName) */
  AND gp.OBJECT_NAME IN /*objectName;type=NVARCHAR*/('%')
  /*end*/
  AND gp.COLUMN_NAME IS NULL
ORDER BY gp.GRANTEE, gp.GRANTOR, gp.SCHEMA_NAME, gp.OBJECT_NAME, gp.PRIVILEGE
