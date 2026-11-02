SELECT
    s.*
  , t.ENGINE
  , t.TABLE_COMMENT
FROM information_schema.SEQUENCES s
LEFT JOIN information_schema.TABLES t
  ON t.TABLE_CATALOG = s.SEQUENCE_CATALOG
 AND t.TABLE_SCHEMA = s.SEQUENCE_SCHEMA
 AND t.TABLE_NAME = s.SEQUENCE_NAME
 AND t.TABLE_TYPE = 'SEQUENCE'
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND SEQUENCE_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND SEQUENCE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND SEQUENCE_NAME IN /*sequenceName*/('%')
  /*end*/
ORDER BY SEQUENCE_SCHEMA, SEQUENCE_NAME
