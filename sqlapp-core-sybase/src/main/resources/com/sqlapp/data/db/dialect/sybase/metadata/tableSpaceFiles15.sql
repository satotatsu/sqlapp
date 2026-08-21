SELECT
  DB_NAME() AS catalog_name
, u.vdevno AS file_id
, u.size
, d.name
, d.phyname AS physical_name
, s.name AS file_group_name
, u.lstart AS logical_start_page
, u.unreservedpgs AS unreserved_pages
FROM syssegments s
INNER JOIN master.dbo.sysusages u
  ON u.dbid=DB_ID()
  AND (u.segmap & CONVERT(INT, POWER(2, s.segment))) != 0
INNER JOIN master.dbo.sysdevices d
  ON u.vdevno=d.vdevno
WHERE 1=1
  /*if isNotEmpty(tableSpaceName) */
  AND s.name IN /*tableSpaceName;type=VARCHAR*/('%')
  /*end*/
ORDER BY s.segment, u.lstart
