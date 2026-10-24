SELECT t.TABSCHEMA AS schema_name, t.TABNAME AS table_name,
       v.TEXT, t.REMARKS, t.CREATE_TIME, t.ALTER_TIME,
       t.TBSPACE, t.INDEX_TBSPACE, t.LONG_TBSPACE, t.REFRESH,
       v.VALID, v.QUALIFIER, v.FUNC_PATH
FROM SYSCAT.TABLES t
JOIN SYSCAT.VIEWS v ON v.VIEWSCHEMA=t.TABSCHEMA AND v.VIEWNAME=t.TABNAME
WHERE t.TYPE='S'
/*if isNotEmpty(schemaName)*/
  AND rtrim(t.TABSCHEMA) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
  AND rtrim(t.TABNAME) IN /*tableName*/('%')
/*end*/
ORDER BY t.TABSCHEMA, t.TABNAME
WITH UR
