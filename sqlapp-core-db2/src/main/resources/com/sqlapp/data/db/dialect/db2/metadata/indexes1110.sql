SELECT
    i.INDSCHEMA AS schema_name
  , i.TABNAME AS table_name
  , i.TABSCHEMA AS table_schema
  , i.INDNAME AS index_name
  , ic.COLSEQ AS ORDINAL_POSITION
  , ic.COLNAME AS column_name
  , ic.COLORDER
  , ic.TEXT AS key_expression
  , ic.VIRTUAL AS key_virtual
  , xp.PATTERN AS XML_PATTERN
  , xp.TYPEMODEL AS XML_TYPEMODEL
  , xp.DATATYPE AS XML_DATATYPE
  , xp.HASHED AS XML_HASHED
  , xp.LENGTH AS XML_LENGTH
  , xp.SCALE AS XML_SCALE
  , ts.TBSPACE AS table_space
  , i.*
FROM SYSCAT.INDEXES i
LEFT JOIN SYSCAT.INDEXCOLUSE ic
  ON (ic.INDSCHEMA=i.INDSCHEMA
  AND ic.INDNAME=i.INDNAME)
LEFT JOIN SYSCAT.INDEXXMLPATTERNS xp
  ON xp.INDSCHEMA=i.INDSCHEMA AND xp.INDNAME=i.INDNAME AND i.INDEXTYPE='XVIL'
LEFT OUTER JOIN SYSCAT.TABLESPACES ts
  ON (i.TBSPACEID=ts.TBSPACEID)
WHERE i.INDEXTYPE NOT IN ('XPTH','XRGN','XVIP')
  /*if isNotEmpty(schemaName)*/
  AND rtrim(i.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(i.TABNAME) IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND rtrim(i.INDNAME) IN /*indexName*/('%')
  /*end*/
ORDER BY i.INDSCHEMA, i.TABNAME, i.INDNAME, ic.COLSEQ
WITH UR
