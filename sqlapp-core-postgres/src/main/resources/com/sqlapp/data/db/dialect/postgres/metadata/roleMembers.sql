SELECT
  current_database() AS catalog_name
, grt.rolname AS grantor
, gep.rolname AS grantee
, grp.rolname AS role_name
, am.admin_option
FROM pg_catalog.pg_auth_members am
INNER JOIN pg_catalog.pg_authid grp
  ON (am.roleid=grp.oid)
INNER JOIN pg_catalog.pg_authid gep
  ON (am.member=gep.oid)
INNER JOIN pg_catalog.pg_authid grt
  ON (am.grantor=grt.oid)
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND gep.rolname IN /*grantee*/('%')
  /*end*/
ORDER BY gep.rolname, grp.rolname