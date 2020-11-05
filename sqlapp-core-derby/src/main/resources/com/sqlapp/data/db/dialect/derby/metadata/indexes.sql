SELECT
   c.CONGLOMERATENAME AS index_name
 , c.DESCRIPTOR AS index_info
 , c.*
 , t.TABLENAME AS table_name
 , s.SCHEMANAME AS schema_name
FROM SYS.SYSCONGLOMERATES c
INNER JOIN SYS.SYSTABLES t
  ON (c.TABLEID=t.TABLEID)
INNER JOIN SYS.SYSSCHEMAS s
  ON (c.SCHEMAID=s.SCHEMAID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND s.SCHEMANAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLENAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND c.CONGLOMERATENAME IN /*indexName*/('%')
  /*end*/
--  AND c.ISINDEX=true
ORDER BY s.SCHEMANAME, t.TABLENAME, c.CONGLOMERATENAME