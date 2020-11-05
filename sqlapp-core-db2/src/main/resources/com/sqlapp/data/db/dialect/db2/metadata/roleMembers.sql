SELECT
  rm.ROLENAME AS role_name
, rm.*
FROM SYSCAT.ROLEAUTH rm
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND rtrim(rm.GRANTEE) IN /*grantee*/('%')
  /*end*/
ORDER BY rm.GRANTEE, rm.ROLENAME
WITH UR
