SELECT
r.*
FROM ROLES r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.ROLE_NAME IN /*roleName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY r.ROLE_MODE, r.ROLE_NAME
