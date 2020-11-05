SELECT
    DB_NAME() AS catalog_name
  , a.*
FROM sys.assemblies a
WHERE 1=1
--  AND a.is_user_defined=1
  /*if isNotEmpty(assemblyName)*/
  AND a.name IN /*assemblyName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY a.name
