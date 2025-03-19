SELECT DISTINCT
  current_database() AS constraint_catalog
  , nc.nspname AS constraint_schema
  , c.conname AS constraint_name
  , current_database() AS table_catalog
  , nr.nspname AS table_schema
  , r.relname AS table_name
  , a.attname AS column_name
  , CASE c.contype 
    WHEN 'c' THEN 'CHECK' 
    WHEN 'f' THEN 'FOREIGN KEY' 
    WHEN 'p' THEN 'PRIMARY KEY' 
    WHEN 'u' THEN 'UNIQUE' 
    END AS constraint_type
  , pg_get_constraintdef(c.oid) as consrc --êßñÒéÆ
  , c.condeferrable AS is_deferrable
  , c.condeferred AS initially_deferred 
  , d.refobjsubid
FROM pg_catalog.pg_constraint c
INNER JOIN pg_catalog.pg_class r
  ON (c.conrelid = r.oid)
INNER JOIN pg_catalog.pg_depend d
  ON (c.oid = d.objid
  AND r.oid = d.refobjid)
INNER JOIN pg_catalog.pg_attribute a
  ON (r.oid = a.attrelid
  AND d.refobjsubid = a.attnum )
INNER JOIN pg_catalog.pg_namespace nc
  ON (c.connamespace = nc.oid)
INNER JOIN pg_catalog.pg_namespace nr
  ON (r.relnamespace = nr.oid)
WHERE 1=1
  AND c.contype NOT IN ('t', 'x')
  AND r.relkind IN ('r','p') 
  AND c.contype = 'c'
  AND (NOT pg_is_other_temp_schema(nr.oid)) 
  AND NOT attisdropped
  /*if isNotEmpty(schemaName)*/
  AND nr.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND r.relname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.conname IN /*constraintName*/('%')
  /*end*/
ORDER BY nr.nspname, r.relname, c.conname, d.refobjsubid