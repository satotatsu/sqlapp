SELECT
    DB_NAME() AS catalog_name
  , s.name AS schema_name
  , s.schema_id
  , s.principal_id
FROM sys.schemas s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName*/('%')
  /*end*/
  AND s.name NOT IN (
	SELECT dp.name AS role_name
	FROM sys.database_principals dp
	WHERE dp.is_fixed_role=1
  )
 　 AND s.name NOT IN ('INFORMATION_SCHEMA')
ORDER BY s.name