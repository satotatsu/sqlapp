SELECT
   *
FROM information_schema.roles
WHERE 1=1
  /*if isNotEmpty(roleName)*/
  AND name IN /*roleName*/('%')
  /*end*/
ORDER BY name