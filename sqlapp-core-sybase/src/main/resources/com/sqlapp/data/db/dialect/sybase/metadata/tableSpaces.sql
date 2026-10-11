SELECT
  DB_NAME() AS catalog_name
, s.name AS file_group_name
FROM syssegments s
WHERE 1=1
  /*if isNotEmpty(tableSpaceName)*/
  AND s.name IN /*tableSpaceName;type=VARCHAR*/('%')
  /*end*/
ORDER BY s.segment
