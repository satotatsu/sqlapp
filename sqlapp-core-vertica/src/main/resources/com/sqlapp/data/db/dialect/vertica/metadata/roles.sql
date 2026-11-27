SELECT
  r.*
FROM V_CATALOG.ROLES r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.name IN /*roleName*/('%')
  /*end*/
ORDER BY r.name
