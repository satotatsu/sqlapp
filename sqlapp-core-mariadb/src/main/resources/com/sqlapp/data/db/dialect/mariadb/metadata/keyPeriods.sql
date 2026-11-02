SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, PERIOD_NAME
FROM information_schema.KEY_PERIOD_USAGE
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME
