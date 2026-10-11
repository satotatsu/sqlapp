SELECT
  c.*
FROM CONSTRAINTS c
WHERE c.CHECK_CONDITION IS NOT NULL
  /*if isNotEmpty(schemaName)*/
  AND c.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.CONSTRAINT_NAME IN /*constraintName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY c.SCHEMA_NAME, c.TABLE_NAME, c.CONSTRAINT_NAME
