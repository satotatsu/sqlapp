SELECT
    DB_NAME() AS catalog_name
  , xsc.xml_collection_id
  , s.name AS schema_name
  , xsc.name AS xml_schema_name
  , XML_SCHEMA_NAMESPACE(s.name, xsc.name) AS text
FROM sys.xml_schema_collections AS xsc 
INNER JOIN sys.schemas AS s 
  ON (xsc.schema_id = s.schema_id)
WHERE xsc.xml_collection_id <> 1
  /*if isNotEmpty(schemaName) */
  AND s.name IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND xsc.name IN /*xmlSchemaName*/('%')
  /*end*/
ORDER BY s.name, xsc.name
