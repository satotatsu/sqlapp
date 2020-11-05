SELECT
    DB_NAME() AS catalog_name
  , schema_name(cc.schema_id) AS schema_name
  , cc.name AS constraint_name
  , OBJECT_NAME(cc.parent_object_id) AS table_name
  , COL_NAME(cc.parent_object_id, cc.parent_column_id) AS column_name
  , OBJECTPROPERTY(cc.object_id, 'CnstIsColumn')
  AS is_column_check_constraint  --単一の列に対するチェック制約(1 or 0)
  , o.type
  , cc.object_id
  , cc.parent_column_id
  , cc.definition
  , cc.is_disabled
  , cc.is_not_trusted
  , cc.is_not_for_replication
  , cc.type_desc
FROM sys.check_constraints cc 
INNER JOIN sys.objects o
  ON (cc.parent_object_id=O.object_id) 
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND schema_name(cc.schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND OBJECT_NAME(cc.parent_object_id) IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY schema_name(cc.schema_id), OBJECT_NAME(cc.parent_object_id)