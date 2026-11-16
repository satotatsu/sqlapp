SELECT
  rtrim(c.TABSCHEMA) AS schema_name
, rtrim(c.TABNAME) AS table_name
, rtrim(c.COLNAME) AS column_name
, c.DIMENSION
, c.COLSEQ
, c.TYPE
FROM SYSCAT.COLUSE c
WHERE c.TYPE = 'C'
  /*if isNotEmpty(schemaName)*/
  AND c.TABSCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABNAME IN /*tableName*/('%')
  /*end*/
ORDER BY c.TABSCHEMA, c.TABNAME, c.DIMENSION, c.COLSEQ
