SELECT username AS role_name
FROM sysusers
WHERE usertype = 'G'
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(roleName)*/
  AND username IN /*roleName*/('%')
  /*end*/
ORDER BY username
