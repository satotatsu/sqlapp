SELECT
  s.catalog_name
FROM information_schema.schemata s
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND catalog_name IN /*catalogName*/('%')
  /*end*/
GROUP BY catalog_name
ORDER BY catalog_name
