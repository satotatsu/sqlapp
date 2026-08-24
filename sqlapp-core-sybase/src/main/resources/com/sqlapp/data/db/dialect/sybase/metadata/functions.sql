SELECT
db_name() AS specific_catalog
, user_name(o.uid) AS specific_schema
, o.name AS routine_name
, o.name AS specific_name
, o.crdate AS created
, o.crdate AS last_altered
, c.text AS routine_definition
FROM sysobjects o
INNER JOIN syscomments c ON o.id=c.id
WHERE o.type IN ('F', 'SF')
  /*if isNotEmpty(catalogName) */
  AND db_name() IN /*catalogName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND user_name(o.uid) IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(functionName) */
  AND o.name IN /*functionName;type=VARCHAR*/('%')
  /*end*/
ORDER BY user_name(o.uid), o.name, c.colid
