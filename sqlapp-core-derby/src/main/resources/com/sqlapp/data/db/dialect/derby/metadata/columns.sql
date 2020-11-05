/**
 * 未使用
 */
SELECT c.*
, c.COLUMNNAME AS column_name
, t.TABLENAME AS table_name
, S.SCHEMANAME AS schema_name
/**
 * COLUMNDATATYPE
DOLUMNDEFAULT
AUTOINCREMENTVALUE
AUTOINCREMENTSTART
AUTOINCREMENTINC
 */
FROM SYS.SYSCOLUMNS c
INNER JOIN SYS.SYSTABLES t
  ON (c.REFERENCEID=t.TABLEID)
INNER JOIN SYS.SYSSCHEMAS S
  ON (T.SCHEMAID=S.SCHEMAID)
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND s.SCHEMANAME IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLENAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.COLUMNNAME IN /*columnName*/('%')
  /*end*/
ORDER BY s.SCHEMANAME, t.TABLENAME, c.COLUMNNUMBER
