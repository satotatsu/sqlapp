SELECT
  DBINFO('dbname') AS catalog_name
, t.owner AS schema_name
, c.constrname AS constraint_name
, t.tabname AS table_name
, k.checktext AS definition
FROM sysconstraints c
INNER JOIN systables t
  ON (c.tabid = t.tabid)
INNER JOIN syschecks k
  ON (c.constrid = k.constrid)
WHERE c.constrtype = 'C'
  AND k.type = 'T'
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, c.constrname, k.seqno
