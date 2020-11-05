SELECT
  DB_NAME() AS catalog_name
, gep.name AS grantee
, grp.name AS role_name
FROM sysmembers rm
INNER JOIN sysusers grp
  ON (rm.groupuid=grp.uid)
INNER JOIN sysusers gep
  ON (rm.memberuid=gep.uid)
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND gep.name IN /*grantee;type=NVARCHAR*/('%')
  /*end*/
ORDER BY gep.name, grp.name
