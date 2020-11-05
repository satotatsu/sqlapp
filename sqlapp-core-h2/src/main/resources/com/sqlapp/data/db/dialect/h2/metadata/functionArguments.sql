SELECT
fa.*
FROM INFORMATION_SCHEMA.FUNCTION_COLUMNS fa
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND fa.alias_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND fa.alias_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND fa.alias_name IN /*routineName*/('%')
  /*end*/
ORDER BY fa.alias_catalog, fa.alias_schema, fa.alias_name, fa.pos