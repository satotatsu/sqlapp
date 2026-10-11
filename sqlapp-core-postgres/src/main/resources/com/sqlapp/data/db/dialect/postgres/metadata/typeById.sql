SELECT
	  current_database() type_catalog
	, n.nspname AS type_schema
 , CASE WHEN en.nspname = 'pg_catalog' THEN format_type(et.oid, null)
        ELSE quote_ident(en.nspname) || '.' || quote_ident(et.typname) END AS type_name
 , CASE WHEN et.oid <> t.oid THEN 1 ELSE 0 END AS array_dimension
	, t.typtype
	, t.oid
	, t.typbasetype
	, t.typnotnull
	, t.typndims
	, t.typdefault
FROM pg_catalog.pg_type t
INNER JOIN pg_catalog.pg_namespace n
 ON (t.typnamespace = n.oid)
INNER JOIN pg_catalog.pg_type et
 ON et.oid = CASE WHEN t.typelem <> 0 AND t.typinput = 'pg_catalog.array_in'::regproc THEN t.typelem ELSE t.oid END
INNER JOIN pg_catalog.pg_namespace en ON et.typnamespace = en.oid
WHERE 1=1
--  AND t.typtype = 'b'
  AND t.oid IN /*typeId*/(1)
