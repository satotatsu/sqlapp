SELECT
  DB_NAME() AS catalog_name
, f.groupname AS file_group_name
FROM sysfilegroups f
WHERE 1=1
  /*if isNotEmpty(tableSpaceName)*/
  AND f.groupname IN /*tableSpaceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY f.groupname