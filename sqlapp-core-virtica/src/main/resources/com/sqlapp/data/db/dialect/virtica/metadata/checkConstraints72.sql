SELECT
  cc.*
  , t.TABLE_SCHEMA
  , cm.COMMENT AS REMARKS
FROM V_CATALOG.TABLE_CONSTRAINTS cc
INNER JOIN V_CATALOG.TABLES t ON cc.TABLE_ID=t.TABLE_ID
LEFT OUTER JOIN V_CATALOG.COMMENTS cm ON cc.CONSTRAINT_ID=cm.OBJECT_ID
WHERE cc.CONSTRAINT_TYPE='c'
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND cc.CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY t.TABLE_SCHEMA, cc.TABLE_NAME, cc.CONSTRAINT_NAME
