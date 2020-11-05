SELECT
  DB_NAME() AS catalog_name
, s.name AS schema_name
, v.name AS table_name
, v.create_date
, v.modify_date
, v.with_check_option
, OBJECTPROPERTY (v.object_id,'IsSchemaBound') AS is_schema_bound
, OBJECT_DEFINITION(v.object_id) AS definition
FROM sys.views v
INNER JOIN sys.schemas s
  ON (v.schema_id = s.schema_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND v.name IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, v.name