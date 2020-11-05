SELECT
  DB_NAME() AS catalog_name
  , a.name AS assembly_name
  , af.content
  , af.file_id
  , af.name AS file_name 
FROM sys.assemblies a
INNER JOIN sys.assembly_files af
  ON (a.assembly_id = af.assembly_id)
WHERE 1=1
--  AND a.is_user_defined=1
  /*if isNotEmpty(assemblyName)*/
  AND a.name IN /*assemblyName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY a.name, af.file_id
