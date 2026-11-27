SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, con.constrname AS constraint_name
, TRIM(con.owner) AS constraint_owner
, s.state AS constraint_state
, con.constrtype AS constraint_type
, nc.colname AS column_name
FROM systables t
INNER JOIN sysconstraints con ON con.tabid=t.tabid
LEFT JOIN sysobjstate s ON s.objtype='C' AND s.tabid=con.tabid AND s.name=con.constrname AND s.owner=con.owner
LEFT JOIN syscoldepend nd ON nd.constrid=con.constrid AND con.constrtype='N'
LEFT JOIN syscolumns nc ON nc.tabid=con.tabid AND nc.colno=nd.colno
WHERE con.constrtype IN ('P','U','R','C','N')
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner,t.tabname,con.constrname
