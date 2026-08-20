SELECT
  DBINFO('dbname') AS catalog_name
, t.owner AS schema_name
, t.tabname AS table_name
, v.viewtext AS view_definition
FROM systables t
INNER JOIN sysviews v
  ON (t.tabid = v.tabid)
WHERE t.tabtype = 'V'
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, v.seqno
