SELECT
   *
FROM SYSCAT.ROLES r
WHERE 1=1
  /*if isNotEmpty(roleName)*/
  AND r.ROLENAME IN /*roleName*/('%')
  /*end*/
ORDER BY r.ROLENAME
WITH UR
