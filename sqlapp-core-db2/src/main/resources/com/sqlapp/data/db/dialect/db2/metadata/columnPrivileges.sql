SELECT a.TABSCHEMA AS schema_name, a.TABNAME AS object_name, a.*
FROM SYSCAT.COLAUTH a
WHERE a.GRANTOR <> 'SYSIBM'
/*if isNotEmpty(schemaName)*/
  AND rtrim(a.TABSCHEMA) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
  AND rtrim(a.TABNAME) IN /*tableName*/('%')
/*end*/
/*if isNotEmpty(columnName)*/
  AND rtrim(a.COLNAME) IN /*columnName*/('%')
/*end*/
ORDER BY a.TABSCHEMA, a.TABNAME, a.GRANTEE, a.GRANTOR
WITH UR
