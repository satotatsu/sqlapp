SELECT a.TABSCHEMA AS schema_name, a.TABNAME AS object_name, a.*
FROM SYSCAT.COLAUTH a
WHERE a.GRANTOR <> 'SYSIBM'
/*if isNotEmpty(schemaName)*/
  AND a.TABSCHEMA IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
  AND a.TABNAME IN /*tableName*/('%')
/*end*/
/*if isNotEmpty(columnName)*/
  AND a.COLNAME IN /*columnName*/('%')
/*end*/
ORDER BY a.TABSCHEMA, a.TABNAME, a.GRANTEE, a.GRANTOR
WITH UR
