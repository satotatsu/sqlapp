SELECT
    s.SEQSCHEMA AS schema_name
  , s.SEQNAME AS sequence_name
  , s.*
FROM SYSCAT.SEQUENCES s
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND rtrim(s.SEQSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName) */
  AND rtrim(s.SEQNAME) IN /*sequenceName*/('%')
  /*end*/
  AND s.SEQTYPE='S'
ORDER BY s.SEQSCHEMA, s.SEQNAME
WITH UR
