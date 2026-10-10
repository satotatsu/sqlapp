SELECT 
  s.*, pg_catalog.obj_description(c.oid,'pg_class') AS remarks
FROM information_schema.sequences s
JOIN pg_catalog.pg_namespace n ON n.nspname=s.sequence_schema
JOIN pg_catalog.pg_class c ON c.relnamespace=n.oid AND c.relname=s.sequence_name AND c.relkind='S'
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND sequence_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND sequence_name IN /*sequenceName*/('%')
  /*end*/
ORDER BY sequence_schema, sequence_name