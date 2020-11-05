SELECT 
    p.*
FROM procedure_parameters p
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND p.schema_name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.procedure_name IN /*routineName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY p.schema_name, p.procedure_name, p.position