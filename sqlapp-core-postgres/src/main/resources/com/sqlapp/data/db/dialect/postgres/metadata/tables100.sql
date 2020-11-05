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
      WHEN 'p' THEN 'PARTITIONED TABLE'
      ELSE NULL
    END AS table_type
  , d.description AS remarks
  , c.oid AS table_id
  , sp.spcname
  /*FOR VALUES IN ('aa', 'bb')*/
  , pg_get_expr(c.relpartbound, c.oid) AS partition_expression
  , pg_get_partkeydef(c.oid) AS partition_strategy_column
  , pg_get_partition_constraintdef(c.oid)
  , c.*
  , c.relpages::bigint * 8 * 1024 AS data_length
  , case partstrat 
    when 'l' then 'list' 
    when 'r' then 'range'
    end as partition_strategy
  , pn.nspname AS parent_schema_name
  , pc.relname AS parent_table_name
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
LEFT OUTER JOIN pg_partitioned_table pt
  ON (c.oid=pt.partrelid) 
LEFT OUTER JOIN pg_inherits i
  ON (i.inhrelid = c.oid
  AND c.relpartbound IS NOT NULL)
LEFT OUTER JOIN pg_catalog.pg_class pc
  ON (i.inhparent = pc.oid)
LEFT OUTER JOIN pg_catalog.pg_namespace pn
  ON (pc.relnamespace = pn.oid)
WHERE 1=1
  /*if isNotEmpty(oid)*/
  AND c.oid IN /*oid*/(1)
  /*end*/
  /*if isNotEmpty(relkind)*/
  AND c.relkind::varchar IN /*relkind*/('r','p')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.relname IN /*tableName*/('%')
  /*end*/
ORDER BY n.nspname, c.relname