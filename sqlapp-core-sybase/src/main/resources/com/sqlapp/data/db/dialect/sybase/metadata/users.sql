SELECT
    DB_NAME() AS catalog_name
  , u.uid AS principal_id
  , u.name AS user_name
  , l.name AS login_user_name
FROM sysusers u
INNER JOIN master.dbo.syslogins l
  ON (u.suid=l.suid)
WHERE u.suid>=0
  /*if isNotEmpty(userName) */
  AND u.name IN /*userName;type=VARCHAR*/('%')
  /*end*/
ORDER BY u.name
