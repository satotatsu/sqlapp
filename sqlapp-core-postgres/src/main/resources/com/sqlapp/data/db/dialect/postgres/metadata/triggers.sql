SELECT
  current_database() AS trigger_catalog
, n.nspname AS trigger_schema
, t.tgname AS trigger_name
, t.oid
, substring(pg_get_triggerdef(t.oid) from
           position('EXECUTE PROCEDURE' in substring(pg_get_triggerdef(t.oid) from 48)) + 47)
 AS action_statement
, CASE WHEN t.tgtype & 1 = 1 THEN 'ROW' ELSE 'STATEMENT' END
 AS action_orientation
, CASE WHEN t.tgtype & 2 = 2 THEN 'BEFORE' ELSE 'AFTER' END
 AS condition_timing
, CASE WHEN t.tgtype & 4 = 4 THEN 'INSERT' ELSE '' END
 AS is_insert
, CASE WHEN t.tgtype & 8 = 8 THEN 'DELETE' ELSE '' END
 AS is_delete
, CASE WHEN t.tgtype & 16 = 16 THEN 'UPDATE' ELSE '' END
 AS is_update
, tgenabled
, obj_description(t.oid, current_database()) as remarks
, t.*
FROM pg_catalog.pg_namespace n
INNER JOIN pg_catalog.pg_class c
 on (n.oid = c.relnamespace)
INNER JOIN pg_catalog.pg_trigger t
 on (c.oid = t.tgrelid) 
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND t.tgname IN /*triggerName*/('%')
  /*end*/
ORDER BY n.nspname, t.tgname