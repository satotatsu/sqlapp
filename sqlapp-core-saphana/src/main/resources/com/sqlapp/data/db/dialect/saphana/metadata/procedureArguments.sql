SELECT 
    p.*
  , pc.COLUMN_NAME AS TABLE_COLUMN_NAME
  , pc.DATA_TYPE_NAME AS TABLE_COLUMN_DATA_TYPE_NAME
  , pc.LENGTH AS TABLE_COLUMN_LENGTH
  , pc.SCALE AS TABLE_COLUMN_SCALE
  , pc.IS_NULLABLE AS TABLE_COLUMN_IS_NULLABLE
FROM procedure_parameters p
LEFT JOIN PROCEDURE_PARAMETER_COLUMNS pc
  ON pc.SCHEMA_NAME = p.SCHEMA_NAME AND pc.PROCEDURE_NAME = p.PROCEDURE_NAME
 AND pc.PARAMETER_POSITION = p.POSITION
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND p.schema_name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(routineName) */
  AND p.procedure_name IN /*routineName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY p.schema_name, p.procedure_name, p.position, pc.POSITION
