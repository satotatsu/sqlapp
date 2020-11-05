SELECT
*
FROM information_schema.routines
WHERE routine_type='FUNCTION'
  /*if isNotEmpty(catalogName) */
  AND specific_catalog IN /*catalogName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND specific_schema IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND specific_name IN /*functionName;type=NVARCHAR*/('%')
  /*end*/
order by specific_schema, specific_name