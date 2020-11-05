SELECT
  DB_NAME() AS catalog_name
, gep.name AS grantee
, grp.name AS role_name
FROM sys.database_role_members rm
INNER JOIN sys.database_principals grp
  ON (rm.role_principal_id=grp.principal_id)
INNER JOIN sys.database_principals gep
  ON (rm.member_principal_id=gep.principal_id)
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND gep.name IN /*grantee;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gep.name, grp.name