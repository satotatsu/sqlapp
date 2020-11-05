/*if dbaOrUser=='DBA'*/
SELECT
  p.*
FROM DBA_SYS_PRIVS p
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND p.GRANTEE IN /*roleName*/('%')
  /*end*/
  AND p.GRANTEE NOT IN (SELECT u.username FROM DBA_USERS u)
ORDER BY p.GRANTEE
/*end*/

/*if dbaOrUser=='USER'*/
SELECT
  p.USERNAME AS GRANTEE
, p.*
FROM USER_SYS_PRIVS p
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND p.USERNAME IN /*roleName*/('%')
  /*end*/
  AND p.USERNAME NOT IN (SELECT u.username FROM ALL_USERS u)
ORDER BY p.USERNAME
/*end*/
