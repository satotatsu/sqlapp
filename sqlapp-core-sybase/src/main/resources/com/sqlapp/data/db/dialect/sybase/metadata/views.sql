SELECT
db_name() AS table_catalog
, user_name(o.uid) AS table_schema
, o.name AS table_name
, c.text AS view_definition
, (ISNULL(c.status,0) & 1) AS source_hidden
, c.number AS source_group
FROM sysobjects o
INNER JOIN syscomments c ON o.id=c.id AND c.texttype=0
WHERE o.type='V'
  /*if isNotEmpty(catalogName) */
  AND db_name() IN /*catalogName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND user_name(o.uid) IN /*schemaName;type=VARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(viewName) */
  AND o.name IN /*viewName;type=VARCHAR*/('%')
  /*end*/
ORDER BY user_name(o.uid), o.name, c.number, c.colid2, c.colid
