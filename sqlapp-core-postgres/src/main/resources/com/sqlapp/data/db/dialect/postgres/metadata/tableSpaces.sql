SELECT
    a.*
  , b.usename
  , d.description AS remarks
FROM pg_catalog.pg_tablespace a
LEFT OUTER JOIN pg_catalog.pg_user b
  ON (A.spcowner=B.usesysid)
LEFT OUTER JOIN pg_catalog.pg_description d 
  ON (a.oid = d.objoid
  AND d.objsubid = 0) 
WHERE 1=1 
  /*if isNotEmpty(tableSpaceName)*/
  AND a.spcname IN /*tableSpaceName*/('%')
  /*end*/
ORDER BY a.spcname
