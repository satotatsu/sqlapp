/*if dbaOrUser=='DBA'*/
SELECT
  p.*
FROM DBA_SYS_PRIVS p
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND p.GRANTEE IN /*userName*/('%')
  /*end*/
  AND p.GRANTEE IN (SELECT u.username FROM DBA_USERS u)
ORDER BY p.GRANTEE
/*end*/

/*if dbaOrUser=='USER'*/
SELECT
  p.USERNAME AS GRANTEE
, p.*
FROM USER_SYS_PRIVS p
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND p.USERNAME IN /*userName*/('%')
  /*end*/
  AND p.USERNAME IN (SELECT u.username FROM ALL_USERS u)
ORDER BY p.USERNAME
/*end*/
