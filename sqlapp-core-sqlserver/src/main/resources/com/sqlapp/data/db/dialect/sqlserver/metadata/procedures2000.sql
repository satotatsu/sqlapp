SELECT
*
FROM information_schema.routines
WHERE routine_type='PROCEDURE'
  /*if isNotEmpty(catalogName) */
  AND specific_catalog IN /*catalogName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND specific_schema IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND specific_name IN /*procedureName;type=NVARCHAR*/('%')
  /*end*/
order by routine_schema, routine_name