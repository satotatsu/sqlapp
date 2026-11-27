SELECT
  dp.TABSCHEMA AS schema_name
, dp.TABNAME AS table_name
, dp.*
FROM SYSCAT.DATAPARTITIONEXPRESSION dp
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND dp.TABSCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND dp.TABNAME IN /*tableName*/('%')
  /*end*/
ORDER BY dp.TABSCHEMA, dp.TABNAME, dp.DATAPARTITIONKEYSEQ
