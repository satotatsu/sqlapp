SELECT
  dp.TABSCHEMA AS schema_name
, dp.TABNAME AS table_name
, ts.TBSPACE AS table_space
, lts.TBSPACE AS lob_table_space
, its.TBSPACE AS index_table_space
, dp.*
FROM SYSCAT.DATAPARTITIONS dp
LEFT OUTER JOIN SYSCAT.TABLESPACES ts
  ON (dp.TBSPACEID=ts.TBSPACEID)
LEFT OUTER JOIN SYSCAT.TABLESPACES lts
  ON (dp.LONG_TBSPACEID=lts.TBSPACEID)
LEFT OUTER JOIN SYSCAT.TABLESPACES its
  ON (dp.INDEX_TBSPACEID=its.TBSPACEID)
WHERE EXISTS (SELECT 1 FROM SYSCAT.DATAPARTITIONEXPRESSION e
              WHERE e.TABSCHEMA=dp.TABSCHEMA AND e.TABNAME=dp.TABNAME)
  /*if isNotEmpty(schemaName)*/
  AND rtrim(dp.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(dp.TABNAME) IN /*tableName*/('%')
  /*end*/
ORDER BY dp.TABSCHEMA, dp.TABNAME, dp.SEQNO
