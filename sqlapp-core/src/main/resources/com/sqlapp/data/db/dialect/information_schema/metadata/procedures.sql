SELECT
*
FROM information_schema.routines
WHERE routine_type='PROCEDURE'
  /*if isNotEmpty(catalogName) */
  AND routine_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND routine_name IN /*procedureName*/('%')
  /*end*/
ORDER BY routine_schema, routine_name
