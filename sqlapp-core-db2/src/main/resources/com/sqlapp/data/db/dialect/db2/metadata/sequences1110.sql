SELECT
    s.SEQSCHEMA AS schema_name
  , s.SEQNAME AS sequence_name
  , d.TYPESCHEMA AS SEQUENCE_TYPE_SCHEMA
  , d.TYPENAME AS SEQUENCE_TYPE_NAME
  , s.*
FROM SYSCAT.SEQUENCES s
LEFT JOIN SYSCAT.DATATYPES d ON d.TYPEID = s.DATATYPEID AND d.TYPEMODULEID IS NULL
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
