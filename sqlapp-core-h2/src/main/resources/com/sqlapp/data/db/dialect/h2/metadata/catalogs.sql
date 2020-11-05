SELECT
  c.catalog_name
FROM information_schema.catalogs c
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND catalog_name IN /*catalogName*/('%')
  /*end*/
ORDER BY catalog_name
