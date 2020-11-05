SELECT 
    'def' as catalog_name
  , p.db as schema_name
  , p.name as routine_name
  , p.specific_name
  , p.param_list
FROM mysql.proc p
WHERE p.type='FUNCTION'
  /*if isNotEmpty(schemaName) */
  AND p.db IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.name IN /*routineName*/('%')
  /*end*/
ORDER BY p.db, p.name, p.specific_name