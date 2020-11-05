SELECT
  DB_NAME() AS catalog_name
, f.name AS file_group_name
, f.data_space_id
, f.is_default
, f.is_read_only
, f.type
, f.type_desc 
FROM sys.filegroups f
WHERE 1=1
  /*if isNotEmpty(tableSpaceName)*/
  AND f.name IN /*tableSpaceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY f.name