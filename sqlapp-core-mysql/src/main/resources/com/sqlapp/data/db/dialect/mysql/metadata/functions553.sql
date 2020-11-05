SELECT
*
FROM information_schema.routines
WHERE routine_type='FUNCTION'
  /*if isNotEmpty(schemaName) */
  AND routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND routine_name IN /*functionName*/('%')
  /*end*/
order by routine_schema, routine_name