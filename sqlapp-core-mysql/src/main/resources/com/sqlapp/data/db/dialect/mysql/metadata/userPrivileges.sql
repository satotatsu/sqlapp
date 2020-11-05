SELECT
*
FROM information_schema.user_privileges
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
ORDER BY grantee, table_catalog, privilege_type
