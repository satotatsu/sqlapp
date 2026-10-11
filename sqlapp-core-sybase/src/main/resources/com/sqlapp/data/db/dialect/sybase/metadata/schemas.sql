SELECT
    DB_NAME() AS catalog_name
  , u.name AS schema_name
  , u.*
FROM sysusers u
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND u.name IN /*schemaName*/('%')
  /*end*/
ORDER BY u.name
