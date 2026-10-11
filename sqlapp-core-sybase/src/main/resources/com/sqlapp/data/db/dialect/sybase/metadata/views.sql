SELECT
db_name() AS table_catalog
, user_name(o.uid) AS table_schema
, o.name AS table_name
, c.text AS view_definition
FROM sysobjects o
INNER JOIN syscomments c ON o.id=c.id
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
ORDER BY user_name(o.uid), o.name, c.colid
