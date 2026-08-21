SELECT
  DB_NAME() AS catalog_name
, l.name AS grantee
, r.name AS role_name
FROM master.dbo.sysloginroles lr
INNER JOIN master.dbo.syslogins l
  ON (lr.suid=l.suid)
INNER JOIN master.dbo.syssrvroles r
  ON (lr.srid=r.srid)
WHERE 1=1
  /*if isNotEmpty(grantee) */
  AND l.name IN /*grantee;type=VARCHAR*/('%')
  /*end*/
ORDER BY l.name, r.name
