SELECT username AS user_name, usertype AS user_type, defrole AS default_role
FROM sysusers
WHERE usertype <> 'G'
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(userName)*/
  AND username IN /*userName*/('%')
  /*end*/
ORDER BY username
