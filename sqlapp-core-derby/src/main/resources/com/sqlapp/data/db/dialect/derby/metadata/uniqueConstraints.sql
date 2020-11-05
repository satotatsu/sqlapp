SELECT c.*
, s.SCHEMANAME AS schema_name
, c.CONSTRAINTNAME AS constraint_name
, t.TABLENAME AS table_name
, idx.CONGLOMERATENAME AS index_name
, idx.DESCRIPTOR AS index_info
FROM SYS.SYSCONSTRAINTS c
INNER JOIN SYS.SYSTABLES t
  ON (c.TABLEID=t.TABLEID)
INNER JOIN SYS.SYSSCHEMAS s
  ON (c.SCHEMAID=t.SCHEMAID)
INNER JOIN SYS.SYSCONGLOMERATES idx
  ON (c.CONSTRAINTID=idx.CONGLOMERATEID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND s.SCHEMANAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLENAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.CONSTRAINTNAME IN /*constraintName*/('%')
  /*end*/
ORDER BY s.SCHEMANAME, t.TABLENAME, c.TYPE, c.CONSTRAINTNAME
