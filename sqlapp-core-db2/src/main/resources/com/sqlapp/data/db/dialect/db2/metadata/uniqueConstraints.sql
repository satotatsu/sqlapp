SELECT
  tc.CONSTNAME AS constraint_name
, tc.TABSCHEMA AS schema_name
, tc.TABNAME AS table_name
, tc.ENFORCED
, kc.COLNAME AS column_name
, ic.COLORDER
, tc.TYPE
, i.*
FROM SYSCAT.TABCONST tc
INNER JOIN SYSCAT.KEYCOLUSE kc
  ON (tc.CONSTNAME=kc.CONSTNAME
  AND tc.TABSCHEMA=kc.TABSCHEMA
  AND tc.TABNAME=kc.TABNAME
  )
INNER JOIN SYSCAT.INDEXES i
  ON (tc.TABSCHEMA=i.INDSCHEMA
  AND tc.CONSTNAME=i.INDNAME
  AND tc.TABNAME=i.TABNAME
  )
INNER JOIN SYSCAT.INDEXCOLUSE ic
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
