SELECT r.role_user, r.role_host
FROM (
  SELECT FROM_USER AS role_user, FROM_HOST AS role_host FROM mysql.role_edges
  UNION
  SELECT DEFAULT_ROLE_USER, DEFAULT_ROLE_HOST FROM mysql.default_roles
) r
WHERE 1=1
  /*if isNotEmpty(roleName) || accountHost != null*/
  AND r.role_user IN /*roleName*/('%')
  /*end*/
  /*if accountHost != null*/
  AND r.role_host = /*accountHost*/''
  /*end*/
ORDER BY r.role_user, r.role_host
