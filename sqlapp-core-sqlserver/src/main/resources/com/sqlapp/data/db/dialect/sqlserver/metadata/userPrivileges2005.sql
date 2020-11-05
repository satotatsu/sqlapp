SELECT DISTINCT
  DB_NAME() AS catalog_name
, perm.class
, perm.class_desc  --SERVER SERVER_PRINCIPAL ENDPOINT
--, OBJECT_NAME(perm.major_id) AS object_name
--, perm.major_id
--, perm.minor_id
, perm.permission_name
, state_desc
, gep.name AS grantee
, gop.name AS grantor
FROM sys.server_permissions perm
INNER JOIN sys.database_principals gop
  ON (perm.grantor_principal_id=gop.principal_id)
INNER JOIN sys.database_principals gep
  ON (perm.grantee_principal_id=gep.principal_id)
WHERE 1=1
  /*if isNotEmpty(objectName) */
  AND OBJECT_NAME(perm.major_id) IN /*objectName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gop.name, gep.name, perm.permission_name
