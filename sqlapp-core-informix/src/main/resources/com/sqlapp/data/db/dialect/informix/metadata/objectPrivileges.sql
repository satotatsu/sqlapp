SELECT TRIM(DBINFO('dbname')) AS catalog_name, TRIM(t.owner) AS schema_name
, t.tabname AS table_name, a.grantor, a.grantee, a.tabauth AS table_auth
FROM systabauth a
INNER JOIN systables t ON t.tabid = a.tabid
WHERE 1 = 1
  /*if isNotEmpty(catalogName)*/
  AND TRIM(DBINFO('dbname')) IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND t.tabname IN /*objectName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, a.grantee, a.grantor
