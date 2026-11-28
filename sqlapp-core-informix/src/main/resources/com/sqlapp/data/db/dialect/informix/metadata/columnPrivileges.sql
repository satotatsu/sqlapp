SELECT TRIM(DBINFO('dbname')) AS catalog_name, TRIM(t.owner) AS schema_name
, t.tabname AS table_name, c.colname AS column_name, a.grantor, a.grantee, a.colauth AS column_auth
FROM syscolauth a
INNER JOIN systables t ON t.tabid = a.tabid
INNER JOIN syscolumns c ON c.tabid = a.tabid AND c.colno = a.colno
WHERE 1 = 1
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.colname IN /*columnName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, c.colno, a.grantee, a.grantor
