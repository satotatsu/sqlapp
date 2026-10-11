SELECT n.nspname AS schema_name, t.relname AS table_name, c.relname AS index_name,
 (SELECT string_agg(pg_catalog.pg_get_indexdef(c.oid, k, false) || ' ' ||
   pg_catalog.quote_ident(opn.nspname) || '.' || pg_catalog.quote_ident(op.opcname), ', ' ORDER BY k)
  FROM generate_series(1, i.indnkeyatts) k
  JOIN pg_catalog.pg_opclass op ON op.oid=i.indclass[k-1]
  JOIN pg_catalog.pg_namespace opn ON opn.oid=op.opcnamespace) AS keys,
 (SELECT string_agg(option_name || '=' || pg_catalog.quote_literal(option_value), ', ' ORDER BY option_name)
  FROM pg_catalog.pg_options_to_table(c.reloptions)) AS options,
 sp.spcname AS tablespace, distance.opcname AS distance
FROM pg_catalog.pg_index i
JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid
JOIN pg_catalog.pg_class t ON t.oid=i.indrelid
JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
JOIN pg_catalog.pg_am am ON am.oid=c.relam
JOIN pg_catalog.pg_opclass distance ON distance.oid=i.indclass[0]
LEFT JOIN pg_catalog.pg_tablespace sp ON sp.oid=c.reltablespace
WHERE am.amname='scann'
 /*if isNotEmpty(schemaName) */ AND n.nspname IN /*schemaName*/('%') /*end*/
 /*if isNotEmpty(tableName) */ AND t.relname IN /*tableName*/('%') /*end*/
 /*if isNotEmpty(indexName) */ AND c.relname IN /*indexName*/('%') /*end*/
ORDER BY n.nspname,t.relname,c.relname
