SELECT
  r.CONSTNAME AS constraint_name
, r.TABSCHEMA AS schema_name
, r.TABNAME AS table_name
, r.REFKEYNAME AS pk_name
, r.REFTABSCHEMA AS ref_schema_name
, r.REFTABNAME AS ref_table_name
, r.DELETERULE AS delete_rule
, r.UPDATERULE AS update_rule
, r.CREATE_TIME
, fkc.COLNAME AS column_name
, pkc.COLNAME AS ref_column_name
, tc.ENFORCED
, tc.REMARKS
FROM SYSCAT.TABCONST tc
INNER JOIN SYSCAT.REFERENCES r
  ON (tc.CONSTNAME=r.CONSTNAME
  AND tc.TABSCHEMA=r.TABSCHEMA
  AND tc.TABNAME=r.TABNAME
  )
INNER JOIN SYSCAT.KEYCOLUSE fkc
  ON (r.CONSTNAME=fkc.CONSTNAME
  AND r.TABSCHEMA=fkc.TABSCHEMA
  AND r.TABNAME=fkc.TABNAME
  )
INNER JOIN SYSCAT.KEYCOLUSE pkc
  ON (r.REFKEYNAME=pkc.CONSTNAME
  AND r.REFTABSCHEMA=pkc.TABSCHEMA
  AND r.REFTABNAME=pkc.TABNAME
  AND fkc.COLSEQ=pkc.COLSEQ)
WHERE 1=1
  AND tc.TYPE='F'
  /*if isNotEmpty(schemaName)*/
  AND rtrim(r.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(r.TABNAME) IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND rtrim(r.CONSTNAME) IN /*constraintName*/('%')
  /*end*/
ORDER BY r.TABSCHEMA, r.TABNAME, r.CONSTNAME,  fkc.COLSEQ
WITH UR
