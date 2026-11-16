SELECT
  c.TABSCHEMA AS schema_name
, c.TABNAME AS table_name
, c.COLNAME AS column_name
, c.DEFAULT AS default_value
, ci.*
, c.*
FROM SYSCAT.COLUMNS c
LEFT OUTER JOIN SYSCAT.COLIDENTATTRIBUTES ci
  ON (c.TABSCHEMA=ci.TABSCHEMA
  AND c.TABNAME=ci.TABNAME
  AND c.COLNAME=ci.COLNAME)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND c.TABSCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABNAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.COLNAME IN /*columnName*/('%')
  /*end*/
ORDER BY c.TABSCHEMA, c.TABNAME, c.COLNO
WITH UR
