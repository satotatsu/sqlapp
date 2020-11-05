SELECT
    s.*
  , s.SEQ_IN_INDEX AS ORDINAL_POSITION
  , s.COLLATION AS ASC_OR_DESC
FROM information_schema.statistics s
LEFT OUTER JOIN information_schema.table_constraints tc
  ON (s.INDEX_NAME=tc.constraint_name
  AND s.TABLE_SCHEMA=tc.constraint_schema
  AND s.TABLE_NAME=tc.table_name
--  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
  AND tc.constraint_type IN ('PRIMARY KEY')
)
WHERE TRUE 
  /*if isNotEmpty(schemaName)*/
  AND s.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND s.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND s.INDEX_NAME IN /*indexName*/('%')
  /*end*/
  AND tc.constraint_type IS NULL
ORDER BY s.TABLE_CATALOG, s.TABLE_SCHEMA, s.TABLE_NAME, s.INDEX_NAME, s.SEQ_IN_INDEX