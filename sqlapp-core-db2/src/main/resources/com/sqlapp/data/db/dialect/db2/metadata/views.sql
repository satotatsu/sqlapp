SELECT
    v.VIEWSCHEMA AS schema_name
  , v.VIEWNAME AS table_name
  , v.*
FROM SYSCAT.VIEWS v
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND rtrim(v.VIEWSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(v.VIEWNAME) IN /*tableName*/('%')
  /*end*/
ORDER BY v.VIEWSCHEMA, v.VIEWNAME
WITH UR

