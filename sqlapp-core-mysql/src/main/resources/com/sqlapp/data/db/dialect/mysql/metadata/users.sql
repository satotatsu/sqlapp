SELECT
 *
FROM mysql.user u
WHERE 1=1
  /*if isNotEmpty(userName) || accountHost != null */
  AND User IN /*userName;type=NVARCHAR*/('%')
  /*end*/
  /*if accountHost != null */
  AND Host = /*accountHost;type=NVARCHAR*/''
  /*end*/
ORDER BY Host, User
