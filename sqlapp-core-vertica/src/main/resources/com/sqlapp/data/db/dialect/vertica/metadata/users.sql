SELECT
  u.*
FROM V_CATALOG.USERS u
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND u.USER_NAME IN /*userName*/('%')
  /*end*/
ORDER BY u.USER_NAME
