SELECT
  DB_NAME() AS catalog_name
, perm.class
, perm.class_desc
, OBJECT_NAME(perm.major_id) AS object_name
, SCHEMA_NAME(o.schema_id) AS schema_name
, perm.permission_name
, state_desc          --DENY REVOKE GRANT GRANT_WITH_GRANT_OPTION
, gep.name AS grantee
, gop.name AS grantor
FROM sys.database_permissions perm
INNER JOIN sys.database_principals gop
  ON (perm.grantor_principal_id=gop.principal_id)
INNER JOIN sys.database_principals gep
  ON (perm.grantee_principal_id=gep.principal_id)
INNER JOIN sys.objects o
  ON (perm.major_id=o.object_id)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND SCHEMA_NAME(o.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(objectName) */
  AND OBJECT_NAME(perm.major_id) IN /*objectName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gop.name, gep.name, OBJECT_NAME(perm.major_id), perm.permission_name