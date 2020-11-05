SELECT
  d.*
  , t.*
FROM pg_database d
LEFT OUTER JOIN pg_catalog.pg_tablespace t
  ON (d.dattablespace=t.oid)
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND datname IN /*catalogName*/('%')
  /*end*/
ORDER BY datname