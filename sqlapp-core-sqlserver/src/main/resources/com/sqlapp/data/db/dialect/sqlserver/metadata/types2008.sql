SELECT
  DB_NAME() AS catalog_name
, SCHEMA_NAME(t.schema_id) AS schema_name
, t.name AS type_name
, t.max_length
, t.precision
, t.scale
, t.collation_name
, t.is_nullable
, t.is_assembly_type
, t.is_table_type
, at.assembly_class
, af.name AS assembly_name
FROM sys.table_types t
LEFT OUTER JOIN sys.assembly_types at
  ON (t.user_type_id = at.user_type_id
  AND t.is_assembly_type = 1)
LEFT OUTER JOIN sys.assemblies af
  ON (at.assembly_id = af.assembly_id) 
WHERE 1=1
--  AND t.is_user_defined=1
  AND t.is_table_type=1
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(t.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(typeName) */
  AND t.name IN /*typeName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(t.schema_id), t.name
