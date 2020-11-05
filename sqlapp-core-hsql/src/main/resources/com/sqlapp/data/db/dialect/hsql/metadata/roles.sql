SELECT
  r.*
FROM information_schema.applicable_roles r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.role_name IN /*roleName*/('%')
  /*end*/
ORDER BY r.role_name
