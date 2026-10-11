SELECT
  tc.CONSTRAINT_CATALOG AS `table_catalog`
, tc.CONSTRAINT_SCHEMA AS `table_schema`
, tc.TABLE_NAME AS `table_name`
, tc.CONSTRAINT_NAME AS `constraint_name`
, cc.CHECK_CLAUSE AS `check_clause`
, cc.SPANNER_STATE AS `spanner_state`
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
INNER JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
  ON (tc.CONSTRAINT_CATALOG=cc.CONSTRAINT_CATALOG
  AND tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA
  AND tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME)
WHERE tc.CONSTRAINT_TYPE='CHECK'
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
