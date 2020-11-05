SELECT
  u.*
FROM information_schema.system_users u
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND u.user_name IN /*userName*/('%')
  /*end*/
ORDER BY u.user_name
