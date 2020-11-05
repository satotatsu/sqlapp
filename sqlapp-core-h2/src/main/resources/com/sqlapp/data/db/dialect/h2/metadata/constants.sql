SELECT
*
FROM information_schema.constants
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND constant_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND constant_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(constantName)*/
  AND constant_name IN /*constantName*/('%')
  /*end*/
ORDER BY constant_catalog, constant_schema, constant_name
