SELECT
  u.User AS ROLE_NAME
FROM mysql.user u
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND u.User IN /*roleName*/('%')
  /*end*/
  AND u.is_role='Y'
ORDER BY u.User
