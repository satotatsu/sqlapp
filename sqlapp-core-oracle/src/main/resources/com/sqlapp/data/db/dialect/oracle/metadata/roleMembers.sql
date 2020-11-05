SELECT
 rm.*
FROM /*$dbaOrUser;length=3*/DBA_ROLE_PRIVS rm
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND rm.GRANTEE IN /*grantee*/('%')
  /*end*/
ORDER BY rm.GRANTEE, rm.GRANTED_ROLE
