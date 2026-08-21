SELECT
  DBINFO('dbname') AS catalog_name
, TRIM(t.owner) AS schema_name
, c.constrname AS constraint_name
, t.tabname AS table_name
, c.constrtype AS constraint_type
, t.tabid AS table_id
, c.idxname AS index_name
FROM sysconstraints c
INNER JOIN systables t
  ON (c.tabid = t.tabid)
WHERE c.constrtype = 'U'
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, c.constrname
