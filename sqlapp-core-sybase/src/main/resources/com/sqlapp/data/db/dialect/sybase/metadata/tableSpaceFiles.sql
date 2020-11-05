SELECT 
  DB_NAME() AS catalog_name
, df.fileid AS file_id
, df.size
, df.maxsize AS max_size
, df.growth
, df.groupid
, df.name
, df.filename AS physical_name
, df.status
, df.perf
, f.groupname AS file_group_name
FROM sysfiles df
INNER JOIN sysfilegroups f
  ON (df.groupid=f.groupid)
WHERE 1=1
  /*if isNotEmpty(tableSpaceName) */
  AND f.groupname IN /*tableSpaceName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY df.fileid
