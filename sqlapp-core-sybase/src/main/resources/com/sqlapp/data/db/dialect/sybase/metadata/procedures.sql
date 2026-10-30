SELECT
db_name() AS specific_catalog
, user_name(o.uid) AS specific_schema
, o.name AS routine_name
, o.name AS specific_name
, o.crdate AS created
, o.crdate AS last_altered
, c.text AS routine_definition
, (ISNULL(c.status,0) & 1) AS source_hidden
, c.number AS source_group
FROM sysobjects o
INNER JOIN syscomments c ON o.id=c.id AND c.texttype=0
WHERE o.type='P'
  /*if isNotEmpty(catalogName) */
  AND db_name() IN /*catalogName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND user_name(o.uid) IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(procedureName) */
  AND o.name IN /*procedureName;type=VARCHAR*/('%')
  /*end*/
ORDER BY user_name(o.uid), o.name, c.number, c.colid2, c.colid
