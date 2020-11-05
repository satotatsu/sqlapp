SELECT 
    rc.*
FROM REFERENTIAL_CONSTRAINTS rc
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rc.SCHEMA_NAME IN /*schemaName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rc.TABLE_NAME IN /*tableName;type=NVARCHAR*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND rc.CONSTRAINT_NAME IN /*constraintName;type=NVARCHAR*/('%')
  /*end*/
ORDER BY rc.SCHEMA_NAME, rc.TABLE_NAME, rc.CONSTRAINT_NAME, rc.POSITION
