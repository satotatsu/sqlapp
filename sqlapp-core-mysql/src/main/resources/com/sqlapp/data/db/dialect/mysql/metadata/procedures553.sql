SELECT
*
FROM information_schema.routines
WHERE routine_type='PROCEDURE'
  /*if isNotEmpty(schemaName) */
  AND routine_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND routine_name IN /*procedureName*/('%')
  /*end*/
order by routine_schema, routine_name
