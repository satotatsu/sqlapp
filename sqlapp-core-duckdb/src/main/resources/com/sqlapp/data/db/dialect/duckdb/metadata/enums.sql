SELECT
  t.database_name AS domain_catalog
, t.schema_name AS domain_schema
, t.type_name AS domain_name
, t.comment AS remarks
, l.label
, l.ordinal_position
FROM duckdb_types() t
CROSS JOIN (
  SELECT
    x.database_name
  , x.schema_name
  , x.type_name
  , UNNEST(x.labels) AS label
  , UNNEST(range(1, len(x.labels) + 1)) AS ordinal_position
  FROM duckdb_types() x
  WHERE x.logical_type = 'ENUM' AND NOT x.internal
) l
WHERE t.logical_type = 'ENUM'
  AND NOT t.internal
  AND t.database_name NOT IN ('system', 'temp')
  AND l.database_name = t.database_name
  AND l.schema_name = t.schema_name
  AND l.type_name = t.type_name
  /*if isNotEmpty(catalogName)*/
  AND t.database_name IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.schema_name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(domainName)*/
  AND t.type_name IN /*domainName*/('%')
  /*end*/
ORDER BY t.database_name, t.schema_name, t.type_name, l.ordinal_position
