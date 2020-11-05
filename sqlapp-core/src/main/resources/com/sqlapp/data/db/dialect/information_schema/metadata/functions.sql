SELECT
*
FROM information_schema.routines
WHERE routine_type='FUNCTION'
  /*if isNotEmpty(catalogName) */
  AND routine_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND routine_name IN /*functionName*/('%')
  /*end*/
ORDER BY routine_schema, routine_name