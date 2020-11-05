SELECT
  current_database() AS catalog_name
  , u.*
FROM pg_catalog.pg_user u
WHERE 1=1
  /*if isNotEmpty(userName) */
  AND u.usename IN /*userName*/('%')
  /*end*/
ORDER BY u.usename
