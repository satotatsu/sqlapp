SELECT
  m.function_catalog
, m.function_schema
, m.function_name
, m.function_type
, m.overload_key
, m.macro_definition
, m.remarks
, m.parameter_name
, m.parameter_type
, m.ordinal_position
FROM (
  SELECT
    f.database_name AS function_catalog
  , f.schema_name AS function_schema
  , f.function_name
  , f.function_type
  , CAST(f.parameters AS VARCHAR) || '|' || CAST(f.parameter_types AS VARCHAR) || '|' || COALESCE(f.macro_definition, '') AS overload_key
  , f.macro_definition
  , f.comment AS remarks
  , UNNEST(CASE WHEN len(f.parameters) = 0 THEN [NULL::VARCHAR] ELSE f.parameters END) AS parameter_name
  , UNNEST(CASE WHEN len(f.parameters) = 0 THEN [NULL::VARCHAR] ELSE CAST(f.parameter_types AS VARCHAR[]) END) AS parameter_type
  , UNNEST(CASE WHEN len(f.parameters) = 0 THEN [0::BIGINT] ELSE range(1, len(f.parameters) + 1) END) AS ordinal_position
  FROM duckdb_functions() f
  WHERE f.function_type IN ('macro', 'table_macro')
    AND NOT f.internal
    AND f.database_name NOT IN ('system', 'temp')
    /*if isNotEmpty(catalogName)*/
    AND f.database_name IN /*catalogName*/('%')
    /*end*/
    /*if isNotEmpty(schemaName)*/
    AND f.schema_name IN /*schemaName*/('%')
    /*end*/
    /*if isNotEmpty(functionName)*/
    AND f.function_name IN /*functionName*/('%')
    /*end*/
) m
ORDER BY m.function_catalog, m.function_schema, m.function_name, m.function_type, m.overload_key, m.ordinal_position
