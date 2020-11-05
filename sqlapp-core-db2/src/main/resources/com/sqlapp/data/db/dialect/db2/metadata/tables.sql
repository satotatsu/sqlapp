SELECT
    t.TABSCHEMA AS schema_name
  , t.TABNAME AS table_name
  , t.TBSPACE AS tablespace_name
  , t.INDEX_TBSPACE AS index_tablespace_name
  , t.LONG_TBSPACE AS lob_tablespace_name
  , t.*
FROM SYSCAT.TABLES t
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(t.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(t.TABNAME) IN /*tableName*/('%')
  /*end*/
  AND t.TYPE IN ('L', 'T', 'U')
ORDER BY t.TABSCHEMA, t.TABNAME
WITH UR

