SELECT
    DB_NAME() AS catalog_name
  , r.srid AS principal_id
  , r.name AS role_name
  , r.status
FROM master.dbo.syssrvroles r
WHERE 1=1
  /*if isNotEmpty(roleName) */
  AND r.name IN /*roleName;type=VARCHAR*/('%')
  /*end*/
ORDER BY r.name
