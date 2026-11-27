SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, col.colno AS column_number
, col.colname AS column_name
FROM systables t
INNER JOIN syscolumns col ON col.tabid=t.tabid
WHERE t.tabtype='T'
  /*if isNotEmpty(catalogName)*/
  AND DBINFO('dbname') IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner,t.tabname,col.colno
