SELECT
      DB_NAME() AS catalog_name
    , SCHEMA_NAME(schema_id) AS schema_name
	, name
	, object_id
	, base_object_name
	, create_date
	, modify_date
FROM sys.synonyms S
WHERE 0=0 
  /*if isNotEmpty(schemaName) */
  AND SCHEMA_NAME(schema_id) IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(synonymName) */
  AND name IN /*synonymName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY SCHEMA_NAME(schema_id), name
