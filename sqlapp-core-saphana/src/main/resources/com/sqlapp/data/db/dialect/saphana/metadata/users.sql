SELECT
u.*
FROM USERS u
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND u.USER_NAME IN /*userName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY u.USER_NAME
