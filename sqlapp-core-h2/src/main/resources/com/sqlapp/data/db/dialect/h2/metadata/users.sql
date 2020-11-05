SELECT
*
FROM information_schema.users
WHERE 1=1
  /*if isNotEmpty(userName)*/
  AND name IN /*userName*/('%')
  /*end*/
ORDER BY name