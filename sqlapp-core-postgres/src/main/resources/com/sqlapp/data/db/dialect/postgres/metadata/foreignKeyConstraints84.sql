SELECT DISTINCT
  current_database() AS constraint_catalog
  , nc.nspname AS constraint_schema
  , c.conname AS constraint_name
  , obj_description(c.constraint_oid, 'pg_constraint') AS remarks
  , current_database() AS table_catalog
  , pr.nspname AS table_schema
  , p.relname AS table_name
  , pc.attname AS column_name
  , c.key_position AS column_index
  , rr.nspname AS referential_table_schema
  , r.relname AS referential_table_name
  , rc.attname AS referential_column_name
  , rc.attnum AS referential_column_index
  , CASE c.contype 
    WHEN 'c' THEN 'CHECK'
    WHEN 'f' THEN 'FOREIGN KEY'
    WHEN 'p' THEN 'PRIMARY KEY'
    WHEN 'u' THEN 'UNIQUE'
    WHEN 'x' THEN 'EXCLUDE'
    END AS constraint_type
  , CASE c.confupdtype
    WHEN 'a' THEN 'NO ACTION'
    WHEN 'r' THEN 'RESTRICT'
    WHEN 'c' THEN 'CASCADE'
    WHEN 'n' THEN 'SET NULL'
    ELSE 'SET DEFAULT'
    END AS update_rule
  , CASE c.confdeltype
    WHEN 'a' THEN 'NO ACTION'
    WHEN 'r' THEN 'RESTRICT'
    WHEN 'c' THEN 'CASCADE'
    WHEN 'n' THEN 'SET NULL'
    ELSE 'SET DEFAULT'
    END AS delete_rule
  , CASE c.confmatchtype
    WHEN 'f' THEN 'FULL'
    WHEN 'p' THEN 'PARTIAL'
    ELSE 'SIMPLE'
    END AS match_option
  , pg_get_constraintdef(c.constraint_oid) as consrc
  , c.condeferrable AS is_deferrable
  , c.condeferred AS initially_deferred 
FROM (
  SELECT source.*, source.oid AS constraint_oid, generate_subscripts(source.conkey, 1) AS key_position
  FROM pg_catalog.pg_constraint source
) c
INNER JOIN pg_catalog.pg_class p
  ON (c.conrelid = p.oid)
INNER JOIN pg_catalog.pg_attribute pc
  ON (p.oid = pc.attrelid
  AND pc.attnum = c.conkey[c.key_position])
INNER JOIN pg_catalog.pg_namespace nc
  ON (c.connamespace = nc.oid)
INNER JOIN pg_catalog.pg_namespace pr
  ON (p.relnamespace = pr.oid)
INNER JOIN pg_catalog.pg_class r
  ON (c.confrelid = r.oid)
INNER JOIN pg_catalog.pg_attribute rc
  ON (r.oid = rc.attrelid
  AND rc.attnum = c.confkey[c.key_position])
INNER JOIN pg_catalog.pg_namespace rr
  ON (r.relnamespace = rr.oid)
WHERE 1=1
  AND c.contype NOT IN ('t', 'x')
  AND p.relkind IN ('r','p') 
  AND c.contype = 'f'
  AND (NOT pg_is_other_temp_schema(pr.oid)) 
  /*if isNotEmpty(schemaName)*/
  AND pr.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND p.relname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.conname IN /*constraintName*/('%')
  /*end*/
ORDER BY pr.nspname, p.relname, c.conname, column_index
