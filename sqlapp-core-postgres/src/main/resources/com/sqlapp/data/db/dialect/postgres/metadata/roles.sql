SELECT
  current_database() AS catalog_name
  , r.*
FROM pg_catalog.pg_roles r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.rolname IN /*roleName*/('%')
  /*end*/
  AND r.rolcanlogin=false
ORDER BY r.rolname
