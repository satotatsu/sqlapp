SELECT
  cc.*
, cm.COMMENT AS REMARKS
FROM V_CATALOG.FOREIGN_KEYS cc
LEFT OUTER JOIN V_CATALOG.COMMENTS cm
  ON (cc.CONSTRAINT_ID=cm.OBJECT_ID)
WHERE 1=1
  AND cc.CONSTRAINT_TYPE='f'
  /*if isNotEmpty(schemaName)*/
  AND cc.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND cc.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND cc.CONSTRAINT_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY cc.TABLE_SCHEMA, cc.TABLE_NAME, cc.CONSTRAINT_NAME, cc.ORDINAL_POSITION
