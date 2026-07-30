SELECT
  c.CONSTANT_CATALOG
, c.CONSTANT_SCHEMA
, c.CONSTANT_NAME
, c.VALUE_DEFINITION AS "SQL"
, c.REMARKS
FROM INFORMATION_SCHEMA.CONSTANTS c
WHERE TRUE
  /*if isNotEmpty(catalogName)*/
  AND c.CONSTANT_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND c.CONSTANT_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(constantName)*/
  AND c.CONSTANT_NAME IN /*constantName*/('%')
  /*end*/
ORDER BY c.CONSTANT_CATALOG, c.CONSTANT_SCHEMA, c.CONSTANT_NAME
