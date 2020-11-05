SELECT
p.*
FROM PROCEDURES p
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND p.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND p.PROCEDURE_NAME IN /*procedureName;type=NVARCHAR*/('%')
  /*end*/
order by p.SCHEMA_NAME, p.PROCEDURE_NAME