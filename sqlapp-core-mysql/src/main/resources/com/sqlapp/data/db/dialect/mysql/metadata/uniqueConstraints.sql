SELECT 
    tc.constraint_catalog
  , tc.constraint_schema
  , tc.constraint_name
  , tc.constraint_type
  , tc.table_name
  , kc.column_name
FROM information_schema.table_constraints tc
INNER JOIN information_schema.key_column_usage kc
  ON (tc.constraint_schema=kc.constraint_schema
  AND tc.constraint_name=kc.constraint_name
  AND tc.table_name=kc.table_name)
WHERE true
  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
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
