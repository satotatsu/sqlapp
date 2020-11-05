SELECT
 *
FROM mysql.user u
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND User IN /*userName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY Host, User
