SELECT
*
FROM information_schema.routines
WHERE routine_type='PROCEDURE'
  /*if isNotEmpty(catalogName)*/
  AND specific_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND specific_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND specific_name IN /*functionName*/('%')
  /*end*/
ORDER BY specific_catalog, specific_schema, specific_name