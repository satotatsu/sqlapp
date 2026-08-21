SELECT
  cc.*
, cm.COMMENT AS REMARKS
FROM V_CATALOG.CONSTRAINT_COLUMNS cc
LEFT OUTER JOIN V_CATALOG.COMMENTS cm
  ON (cc.CONSTRAINT_ID = cm.OBJECT_ID)
WHERE cc.CONSTRAINT_TYPE = 'c'
  /*if isNotEmpty(schemaName)*/
  AND cc.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND cc.CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY cc.TABLE_SCHEMA, cc.TABLE_NAME, cc.CONSTRAINT_NAME, cc.COLUMN_NAME
