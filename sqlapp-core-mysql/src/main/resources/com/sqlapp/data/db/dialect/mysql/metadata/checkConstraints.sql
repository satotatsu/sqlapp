SELECT
    tc.CONSTRAINT_CATALOG
  , tc.CONSTRAINT_SCHEMA
  , tc.TABLE_NAME
  , tc.CONSTRAINT_NAME
  , cc.CHECK_CLAUSE
  , tc.ENFORCED
FROM information_schema.TABLE_CONSTRAINTS tc
INNER JOIN information_schema.CHECK_CONSTRAINTS cc
  ON  tc.CONSTRAINT_CATALOG = cc.CONSTRAINT_CATALOG
  AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
  AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
WHERE tc.CONSTRAINT_TYPE = 'CHECK'
  /*if isNotEmpty(schemaName)*/
  AND tc.CONSTRAINT_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND tc.CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY tc.CONSTRAINT_SCHEMA, tc.TABLE_NAME, tc.CONSTRAINT_NAME
