SELECT t.TABSCHEMA, t.TABNAME, t.CONTROL
FROM SYSCAT.TABLES t
WHERE t.TYPE IN ('T', 'U')
/*if isNotEmpty(schemaName)*/
  AND rtrim(t.TABSCHEMA) IN /*schemaName*/('%')
/*end*/
ORDER BY t.TABSCHEMA, t.TABNAME
WITH UR
