SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, c.constrname AS constraint_name
, c.constrid AS constraint_id
, TRIM(c.owner) AS constraint_owner
, s.state AS constraint_state
, t.tabname AS table_name
, k.checktext AS definition
FROM sysconstraints c
INNER JOIN systables t
  ON (c.tabid = t.tabid)
LEFT JOIN sysobjstate s ON s.objtype='C' AND s.tabid=c.tabid AND s.name=c.constrname AND s.owner=c.owner
LEFT JOIN syschecks k
  ON (c.constrid = k.constrid AND k.type = 'T')
WHERE c.constrtype = 'C'
  /*if isNotEmpty(catalogName)*/
  AND DBINFO('dbname') IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.constrname IN /*constraintName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, c.constrname, c.constrid, k.seqno
