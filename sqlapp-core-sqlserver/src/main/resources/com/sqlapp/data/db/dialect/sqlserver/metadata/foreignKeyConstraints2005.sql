SELECT 
  DB_NAME() AS catalog_name
, fk.name AS constraint_name
, SCHEMA_NAME(t.schema_id) AS schema_name
, SCHEMA_NAME(rt.schema_id) AS referential_schema_name
, fk.*
, t.name AS table_name
, rt.name AS referential_table_name
, COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS column_name
, COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS referential_column_name
FROM sys.foreign_keys fk
INNER JOIN sys.foreign_key_columns fkc
  ON (fk.object_id=fkc.constraint_object_id)
INNER JOIN sys.tables t
  ON (fk.parent_object_id=t.object_id)
INNER JOIN sys.tables rt
  ON (fk.referenced_object_id=rt.object_id)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND SCHEMA_NAME(fk.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(t.schema_id), fk.parent_object_id, fk.name, fkc.parent_column_id
