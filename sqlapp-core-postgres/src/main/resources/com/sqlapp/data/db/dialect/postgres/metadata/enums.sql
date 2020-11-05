SELECT 
  current_database() AS catalog_name
, n.nspname
, t.typname
, e.enumlabel
FROM pg_catalog.pg_enum e
INNER JOIN pg_catalog.pg_type t
  on (e.enumtypid=t.oid)
INNER JOIN pg_catalog.pg_namespace n
 on (t.typnamespace = n.oid) 
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(domainName)*/
  AND t.typname IN /*domainName*/('%')
  /*end*/
ORDER BY n.nspname, t.typname