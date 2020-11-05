SELECT
u.*
, up.*
FROM USERS u
LEFT OUTER JOIN LAST_USED_PASSWORDS up
  ON (u.USER_NAME=up.USER_NAME)
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND u.USER_NAME IN /*userName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY u.USER_NAME
