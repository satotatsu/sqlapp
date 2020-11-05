SELECT
    ic.INDSCHEMA AS schema_name
  , i.TABNAME AS table_name
  , i.TABSCHEMA AS table_schema
  , ic.INDNAME AS index_name
  , ic.COLSEQ AS ORDINAL_POSITION
  , ic.COLNAME AS column_name
  , ic.COLORDER
  , ts.TBSPACE AS table_space
  , i.*
FROM SYSCAT.INDEXCOLUSE ic
INNER JOIN SYSCAT.INDEXES i
  ON (ic.INDSCHEMA=i.INDSCHEMA
  AND ic.INDNAME=i.INDNAME)
LEFT OUTER JOIN SYSCAT.TABLESPACES ts
  ON (i.TBSPACEID=ts.TBSPACEID)
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(ic.INDSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(i.TABNAME) IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND rtrim(ic.INDNAME) IN /*indexName*/('%')
  /*end*/
ORDER BY ic.INDSCHEMA, i.TABNAME, ic.INDNAME, ic.COLSEQ
WITH UR
