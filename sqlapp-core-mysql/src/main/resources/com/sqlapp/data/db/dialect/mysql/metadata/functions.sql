SELECT
    'def' as catalog_name
  , p.*
  , p.db as schema_name
  , p.name as routine_name
  , p.specific_name  
FROM mysql.proc p
WHERE p.type='FUNCTION'
  /*if isNotEmpty(schemaName) */
  AND p.db IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND p.name IN /*functionName*/('%')
  /*end*/
ORDER BY p.db, p.name, p.specific_name
