SELECT
    DB_NAME() AS catalog_name
  , u.uid AS principal_id
  , u.name AS role_name
  , u.createdate AS create_date
  , u.updatedate AS modify_date
FROM sysusers u
WHERE (u.isapprole = 1 OR u.issqlrole=1)
--  AND u.name<>'dbo'
  /*if isNotEmpty(roleName) */
  AND u.name IN /*roleName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY u.name
