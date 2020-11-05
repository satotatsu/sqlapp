SELECT
  d.*
FROM sysdatabases d
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND d.name IN /*catalogName*/('%')
  /*end*/
ORDER BY d.name