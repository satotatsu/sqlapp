SELECT
  r.*
FROM information_schema.ENABLED_ROLES r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.rolname IN /*roleName*/('%')
  /*end*/
  AND r.rolcanlogin=false
ORDER BY r.rolname
