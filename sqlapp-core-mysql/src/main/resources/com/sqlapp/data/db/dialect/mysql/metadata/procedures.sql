SELECT
    'def' as catalog_name
  , p.*
  , p.db as schema_name
  , p.name as routine_name
  , p.specific_name  
FROM mysql.proc p
WHERE p.type='PROCEDURE'
  /*if isNotEmpty(schemaName) */
  AND p.db IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND p.name IN /*procedureName*/('%')
  /*end*/
ORDER BY p.db, p.name, p.specific_name
