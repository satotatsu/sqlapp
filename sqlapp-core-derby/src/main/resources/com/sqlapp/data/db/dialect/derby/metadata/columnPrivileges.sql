SELECT cp.*
, t.TABLENAME AS table_name
, S.SCHEMANAME AS schema_name
FROM SYS.SYSCOLPERMS cp
INNER JOIN SYS.SYSTABLES t
  ON (cp.TABLEID=t.TABLEID)
INNER JOIN SYS.SYSSCHEMAS s
  ON (t.SCHEMAID=s.SCHEMAID)
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND s.SCHEMANAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLENAME IN /*tableName*/('%')
  /*end*/
ORDER BY cp.GRANTOR, cp.GRANTEE, s.SCHEMANAME, t.TABLENAME
