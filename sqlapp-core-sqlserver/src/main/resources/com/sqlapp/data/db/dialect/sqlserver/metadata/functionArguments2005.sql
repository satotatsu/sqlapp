SELECT
    DB_NAME() AS catalog_name
  , s.name AS schema_name
  , o.name AS routine_name
  , param.name AS parameter_name
  , param.max_length
  , param.precision
  , param.scale
  , param.is_readonly
  , param.is_xml_document
  , param.xml_collection_id
  , CAST(param.default_value AS NVARCHAR) AS default_value
  , ty.name AS data_type
  , ty.*
FROM sys.objects o
INNER JOIN sys.schemas s
  ON (o.schema_id = s.schema_id)
INNER JOIN sys.all_parameters param
  ON (o.object_id=param.object_id AND param.parameter_id>=1)
LEFT OUTER JOIN sys.types ty
  ON (param.user_type_id = ty.user_type_id)
WHERE 1=1
AND o.type IN (
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
ORDER BY s.name, o.name, param.parameter_id
