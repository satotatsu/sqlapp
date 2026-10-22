SELECT 
    tc.constraint_catalog
  , tc.constraint_schema
  , tc.constraint_name
  , tc.constraint_type
  , tc.table_name
  , kc.column_name
  , s.*
  , s.COLLATION AS ASC_OR_DESC
FROM information_schema.table_constraints tc
INNER JOIN information_schema.key_column_usage kc
  ON (tc.constraint_schema=kc.constraint_schema
  AND tc.constraint_name=kc.constraint_name
  AND tc.table_name=kc.table_name)
INNER JOIN information_schema.statistics s
  ON (s.TABLE_SCHEMA=tc.constraint_schema
  AND s.TABLE_NAME=tc.table_name
  AND s.INDEX_NAME=tc.constraint_name
  AND s.SEQ_IN_INDEX=kc.ORDINAL_POSITION)
WHERE true
  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
  -- Functional unique indexes must retain every key part via IndexReader.
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics expression_part
    WHERE expression_part.TABLE_SCHEMA=tc.constraint_schema
      AND expression_part.TABLE_NAME=tc.table_name
      AND expression_part.INDEX_NAME=tc.constraint_name
      AND expression_part.COLUMN_NAME IS NULL
  )
  /*if isNotEmpty(schemaName)*/
  AND tc.constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND tc.constraint_name IN /*constraintName*/('%')
  /*end*/
ORDER BY tc.constraint_schema, tc.table_name, tc.constraint_type, tc.constraint_name, kc.ORDINAL_POSITION
