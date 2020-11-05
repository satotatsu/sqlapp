SELECT cc.COLNAME AS column_name
, c.TABNAME AS table_name
, c.TABSCHEMA AS schema_name
, c.CONSTNAME AS constraint_name
, c.*
FROM SYSCAT.COLCHECKS cc
INNER JOIN SYSCAT.CHECKS c
  ON (cc.CONSTNAME=c.CONSTNAME
  AND cc.TABSCHEMA=c.TABSCHEMA
  AND cc.TABNAME=c.TABNAME)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND rtrim(c.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND rtrim(c.TABNAME) IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName) */
  AND rtrim(c.CONSTNAME) IN /*constraintName*/('%')
  /*end*/
ORDER BY c.TABSCHEMA, c.TABNAME, c.CONSTNAME
WITH UR
