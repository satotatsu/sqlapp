SELECT
	  current_database() type_catalog
	, n.nspname AS type_schema
	, t.typname AS type_name
	, t.typtype
	, t.oid
	, t.typbasetype
	, t.typnotnull
	, t.typndims
	, t.typdefault
FROM pg_catalog.pg_type t
INNER JOIN pg_catalog.pg_namespace n
 ON (t.typnamespace = n.oid)
WHERE 1=1
--  AND t.typtype = 'b'
  AND t.oid IN /*typeId*/(1)
