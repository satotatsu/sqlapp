SELECT r.rolename AS role_name, r.grantee, r.is_grantable, u.defrole AS default_role
FROM sysroleauth r
LEFT JOIN sysusers u ON u.username = r.grantee
WHERE 1 = 1
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(grantee)*/
  AND r.grantee IN /*grantee*/('%')
  /*end*/
ORDER BY r.rolename, r.grantee
