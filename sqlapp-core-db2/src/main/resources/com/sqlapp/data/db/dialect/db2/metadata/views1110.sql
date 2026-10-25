SELECT v.VIEWSCHEMA AS schema_name, v.VIEWNAME AS table_name,
       t.REMARKS, t.CREATE_TIME, t.ALTER_TIME, t.TYPE AS DB2_VIEW_TYPE, v.*
FROM SYSCAT.VIEWS v
JOIN SYSCAT.TABLES t ON t.TABSCHEMA=v.VIEWSCHEMA AND t.TABNAME=v.VIEWNAME
WHERE t.TYPE IN ('V', 'W')
  /*if isNotEmpty(schemaName)*/
  AND rtrim(v.VIEWSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(v.VIEWNAME) IN /*tableName*/('%')
  /*end*/
ORDER BY v.VIEWSCHEMA, v.VIEWNAME
WITH UR
