SELECT
*
FROM information_schema.function_aliases
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND alias_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND alias_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND alias_name IN /*functionName*/('%')
  /*end*/
order by alias_catalog, alias_schema, alias_name