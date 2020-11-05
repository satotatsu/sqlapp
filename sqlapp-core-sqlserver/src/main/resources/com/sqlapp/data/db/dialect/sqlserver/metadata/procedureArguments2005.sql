select
    DB_NAME() AS catalog_name
  , s.name as schema_name
  , p.name as routine_name
  , p.type
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
FROM sys.procedures p
INNER JOIN sys.schemas s
  ON (s.schema_id = p.schema_id)
INNER JOIN sys.all_parameters param
  ON (p.object_id=param.object_id AND param.parameter_id>0)
LEFT OUTER JOIN sys.types ty
  ON (param.user_type_id = ty.user_type_id)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND p.name IN /*procedureName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY s.name, p.name, param.parameter_id