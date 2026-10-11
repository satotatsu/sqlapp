SELECT
  p.PROCEDURE_NAME
  , p.OWNER
  , p.LANGUAGE
  , p.SECURITY
  , p.PROCEDURE_ARGUMENTS
  , p.SCHEMA_NAME
FROM V_CATALOG.USER_PROCEDURES p
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND p.SCHEMA_NAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(procedureName)*/
  AND p.PROCEDURE_NAME IN /*procedureName*/('%')
  /*end*/
ORDER BY p.SCHEMA_NAME, p.PROCEDURE_NAME, p.PROCEDURE_ARGUMENTS
