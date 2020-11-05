SELECT
    current_database() AS catalog_name
  , n.nspname AS schema_name
  , c.relname AS table_name
  , CASE c.relkind
      WHEN 'r' THEN 'TABLE' 
      WHEN 'i' THEN 'INDEX' 
      WHEN 'S' THEN 'SEQUENCE' 
      WHEN 'v' THEN 'VIEW' 
      WHEN 'c' THEN 'TYPE'
      ELSE NULL
    END AS table_type
  , CAST(
      CASE
        WHEN pg_has_role(c.relowner, 'USAGE')
          THEN pg_get_viewdef(c.oid)
        ELSE null 
      END AS varchar
    ) AS definition
  , d.description AS remarks
FROM pg_catalog.pg_class c 
INNER JOIN pg_catalog.pg_namespace n
  ON (c.relnamespace = n.oid)
LEFT OUTER JOIN pg_catalog.pg_description d 
  ON (c.oid = d.objoid
  AND d.objsubid = 0) 
WHERE c.relkind ='v'
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.relname IN /*tableName*/('%')
  /*end*/
ORDER BY n.nspname, c.relname