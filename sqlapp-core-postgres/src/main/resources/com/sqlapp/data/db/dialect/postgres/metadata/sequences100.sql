SELECT current_database() AS sequence_catalog, s.schemaname AS sequence_schema,
 s.sequencename AS sequence_name, s.data_type::text AS sequence_type,
 s.start_value,s.min_value,s.max_value,s.increment_by,s.cache_size,s.cycle,s.last_value,
 pg_catalog.obj_description(c.oid,'pg_class') AS remarks
FROM pg_catalog.pg_sequences s
JOIN pg_catalog.pg_namespace n ON n.nspname=s.schemaname
JOIN pg_catalog.pg_class c ON c.relnamespace=n.oid AND c.relname=s.sequencename AND c.relkind='S'
WHERE 1=1
 /*if isNotEmpty(schemaName) */ AND s.schemaname IN /*schemaName*/('%') /*end*/
 /*if isNotEmpty(sequenceName) */ AND s.sequencename IN /*sequenceName*/('%') /*end*/
ORDER BY s.schemaname,s.sequencename
