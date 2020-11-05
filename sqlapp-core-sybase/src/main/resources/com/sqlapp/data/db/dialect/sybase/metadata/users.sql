SELECT
    DB_NAME() AS catalog_name
  , u.uid AS principal_id
  , u.name AS user_name
  , u.createdate AS create_date
  , u.updatedate AS modify_date
  , l.loginname AS login_user_name
FROM sysusers u
INNER JOIN sys.syslogins l
  ON (u.sid=l.sid)
WHERE (u.isntuser = 1 OR u.issqluser=1)
  AND u.name<>'dbo'
  /*if isNotEmpty(userName) */
  AND u.name IN /*userName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY u.name
