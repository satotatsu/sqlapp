SELECT
    DB_NAME() AS catalog_name
  , s.name AS schema_name
  , o.name AS function_name
  , o.create_date
  , o.modify_date
  , o.type
  ,
  CASE
  WHEN COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id)=-2
       THEN 'OWNER'
  WHEN COALESCE(dp.name, sp.name) IS NULL
       THEN 'CALLER'
  ELSE
    'SELF'
  END 
  AS sql_security
  ,
  CASE COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id)
  WHEN -2 THEN null
  ELSE
    COALESCE(dp.name, sp.name)
  END 
  AS execute_as
  , sm.definition
  , sm.is_recompiled
  , sm.uses_ansi_nulls
  , sm.is_schema_bound
  , af.name AS assembly_name
  , am.assembly_class
  , am.assembly_id
  , am.assembly_method
  , COALESCE(sm.null_on_null_input, am.null_on_null_input)
  AS null_on_null_input
  , p.max_length
  , p.precision
  , p.scale
  , p.is_xml_document
  , p.xml_collection_id
  , CAST(p.default_value AS NVARCHAR) AS default_value
  , ty.*
FROM sys.objects o
INNER JOIN sys.schemas s
  ON (o.schema_id = s.schema_id)
LEFT OUTER JOIN sys.all_parameters p
  ON (o.object_id=p.object_id AND p.parameter_id=0)
LEFT OUTER JOIN sys.types ty
  ON (p.user_type_id = ty.user_type_id)
LEFT OUTER JOIN sys.sql_modules sm
  ON (o.object_id=sm.object_id)  
LEFT OUTER JOIN sys.assembly_modules am
  ON (o.object_id = am.object_id)
LEFT OUTER JOIN sys.assemblies af
  ON (am.assembly_id = af.assembly_id) 
LEFT OUTER JOIN sys.database_principals dp
  ON (dp.principal_id=COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id))  
LEFT OUTER JOIN sys.server_principals sp
  ON (sp.principal_id=COALESCE(sm.execute_as_principal_id, am.execute_as_principal_id))
WHERE o.type IN (
	  'AF'  --集計関数 (CLR)
	, 'FN'  --SQL スカラー関数
	, 'FS'  --アセンブリ (CLR)スカラー関数
	, 'FT'  --アセンブリ (CLR) テーブル値関数
	, 'IF'  --SQL インライン テーブル値関数
	, 'TF'  --SQL テーブル値関数
)
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND o.name IN /*functionName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, o.name
