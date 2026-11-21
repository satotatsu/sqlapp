SELECT dbsnum AS space_number, name AS space_name, owner AS space_owner, flags AS space_flags
/*if hasPageSize*/ , pagesize AS page_size /*end*/
FROM sysmaster:"informix".sysdbspaces
WHERE 1 = 1
/*if isNotEmpty(tableSpaceName)*/
  AND name IN /*tableSpaceName*/('%')
/*end*/
ORDER BY dbsnum
