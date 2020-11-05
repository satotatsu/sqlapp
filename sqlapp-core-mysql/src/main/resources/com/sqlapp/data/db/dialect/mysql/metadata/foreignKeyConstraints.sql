SELECT 
    rc.constraint_catalog
  , rc.constraint_schema
  , rc.constraint_name
  , rc.unique_constraint_catalog AS referenced_table_catalog
  , rc.unique_constraint_schema AS referenced_table_schema
  , rc.unique_constraint_name
  , rc.match_option
  , rc.update_rule
  , rc.delete_rule
  , rc.table_name
  , rc.referenced_table_name
  , kc.column_name
  , kc.referenced_column_name
FROM information_schema.referential_constraints rc
INNER JOIN information_schema.key_column_usage kc
  ON (rc.constraint_schema=kc.constraint_schema
  AND rc.constraint_name=kc.constraint_name
  AND rc.table_name=kc.table_name)
WHERE TRUE
  /*if isNotEmpty(schemaName)*/
  AND rc.constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rc.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND rc.constraint_name IN /*constraintName*/('%')
  /*end*/
ORDER BY rc.constraint_schema, rc.table_name, rc.constraint_name, kc.ORDINAL_POSITION
