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
      WHEN 'f' THEN 'FOREIGN TABLE'
      WHEN 'm' THEN 'MATERIALIZED VIEW'
      ELSE NULL
    END AS table_type
  , d.description AS remarks
  , c.oid AS table_id
  , sp.spcname
  , c.*
  , c.relpages::bigint * 8 * 1024 AS data_length
  , tstat.*
FROM pg_catalog.pg_class c 
INNER JOIN pg_catalog.pg_namespace n
  ON (c.relnamespace = n.oid)
LEFT OUTER JOIN pg_catalog.pg_description d 
  ON (c.oid = d.objoid
  AND d.objsubid = 0) 
LEFT OUTER JOIN pg_tablespace sp
  ON (c.reltablespace = sp.oid)
LEFT OUTER JOIN pg_stat_all_tables tstat
  ON (c.oid = tstat.relid) 
WHERE 1=1
  /*if isNotEmpty(oid)*/
  AND c.oid IN /*oid*/(1)
  /*end*/
  /*if isNotEmpty(relkind)*/
  AND c.relkind::varchar IN /*relkind*/('r')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.relname IN /*tableName*/('%')
  /*end*/
ORDER BY n.nspname, c.relname