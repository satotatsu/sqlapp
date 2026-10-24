SELECT
  tc.CONSTNAME AS constraint_name
, tc.TABSCHEMA AS schema_name
, tc.TABNAME AS table_name
, tc.ENFORCED
, kc.COLNAME AS column_name
, ic.COLORDER
, tc.TYPE
, tc.REMARKS
, i.INDNAME AS backing_index_name
, i.INDSCHEMA AS backing_index_schema
, i.*
FROM SYSCAT.TABCONST tc
INNER JOIN SYSCAT.KEYCOLUSE kc
  ON (tc.CONSTNAME=kc.CONSTNAME
  AND tc.TABSCHEMA=kc.TABSCHEMA
  AND tc.TABNAME=kc.TABNAME
  )
LEFT JOIN SYSCAT.CONSTDEP d
  ON (tc.TABSCHEMA=d.TABSCHEMA AND tc.TABNAME=d.TABNAME
  AND tc.CONSTNAME=d.CONSTNAME AND d.BTYPE='I')
LEFT JOIN SYSCAT.INDEXES i
  ON (d.BSCHEMA=i.INDSCHEMA AND d.BNAME=i.INDNAME
  AND tc.TABSCHEMA=i.TABSCHEMA AND tc.TABNAME=i.TABNAME
  )
LEFT JOIN SYSCAT.INDEXCOLUSE ic
  ON (i.INDSCHEMA=ic.INDSCHEMA
  AND i.INDNAME=ic.INDNAME
  AND kc.COLNAME=ic.COLNAME)
WHERE 1=1
  AND tc.TYPE IN ('P', 'U')
  /*if isNotEmpty(schemaName)*/
  AND rtrim(tc.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(tc.TABNAME) IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND rtrim(tc.CONSTNAME) IN /*constraintName*/('%')
  /*end*/
ORDER BY tc.TABSCHEMA, tc.TABNAME, tc.CONSTNAME, tc.TYPE, kc.COLSEQ
WITH UR
