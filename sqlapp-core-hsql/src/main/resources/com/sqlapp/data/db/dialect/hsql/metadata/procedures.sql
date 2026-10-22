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
  /*if isNotEmpty(procedureName) */
  AND routine_name IN /*procedureName*/('%')
  /*end*/
ORDER BY specific_catalog, specific_schema, specific_name
