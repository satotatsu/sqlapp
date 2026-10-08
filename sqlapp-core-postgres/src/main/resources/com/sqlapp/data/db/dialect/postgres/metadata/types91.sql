SELECT
  current_database() AS type_catalog
, n.nspname AS type_schema
, t.typname AS type_name
, CAST(t.oid AS int4) AS oid
, 'CREATE TYPE ' || quote_ident(n.nspname) || '.' || quote_ident(t.typname) || ' AS (' ||
  array_to_string(ARRAY(
    SELECT quote_ident(a.attname) || ' ' ||
      CASE WHEN at.typelem <> 0 AND at.typlen = -1 AND en.nspname <> 'pg_catalog'
           THEN quote_ident(en.nspname) || '.' || quote_ident(et.typname) || repeat('[]', GREATEST(a.attndims, 1))
           WHEN tn.nspname = 'pg_catalog' THEN pg_catalog.format_type(a.atttypid, a.atttypmod) ||
             CASE WHEN a.attndims > 1 THEN repeat('[]', a.attndims - 1) ELSE '' END
           ELSE quote_ident(tn.nspname) || '.' || quote_ident(at.typname) END ||
      CASE WHEN a.attcollation <> 0 THEN ' COLLATE ' || quote_ident(cn.nspname) || '.' || quote_ident(coll.collname)
           ELSE '' END
    FROM pg_catalog.pg_attribute a
    INNER JOIN pg_catalog.pg_type at ON (at.oid = a.atttypid)
    INNER JOIN pg_catalog.pg_namespace tn ON (tn.oid = at.typnamespace)
    LEFT JOIN pg_catalog.pg_type et ON (et.oid = at.typelem)
    LEFT JOIN pg_catalog.pg_namespace en ON (en.oid = et.typnamespace)
    LEFT JOIN pg_catalog.pg_collation coll ON (coll.oid = a.attcollation)
    LEFT JOIN pg_catalog.pg_namespace cn ON (cn.oid = coll.collnamespace)
    WHERE a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
    ORDER BY a.attnum
  ), ', ') || ')' AS definition
, t.typdefault
, obj_description(t.oid, 'pg_type') as remarks
FROM pg_catalog.pg_type t
INNER JOIN pg_catalog.pg_namespace n
  ON (t.typnamespace = n.oid)
INNER JOIN pg_catalog.pg_class c
  ON (t.typrelid = c.oid
 AND t.typtype = c.relkind)
WHERE 1=1
  AND t.typtype = 'c'
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(typeName) */
  AND t.typname IN /*typeName*/('%')
  /*end*/
ORDER BY n.nspname, t.typname
