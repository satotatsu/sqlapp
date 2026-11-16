SELECT a.TABSCHEMA AS schema_name, a.TABNAME AS object_name, a.*
FROM SYSCAT.TABAUTH a
WHERE a.GRANTOR <> 'SYSIBM'
/*if isNotEmpty(schemaName)*/
  AND a.TABSCHEMA IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(objectName)*/
  AND a.TABNAME IN /*objectName*/('%')
/*end*/
ORDER BY a.TABSCHEMA, a.TABNAME, a.GRANTEE, a.GRANTOR
WITH UR
