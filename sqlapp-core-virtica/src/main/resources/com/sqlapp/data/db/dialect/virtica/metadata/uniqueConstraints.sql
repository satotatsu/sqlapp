SELECT
  cc.*
, cm.COMMENT AS REMARKS
FROM V_CATALOG.CONSTRAINT_COLUMNS cc
INNER JOIN V_CATALOG.COLUMNS c
  ON (
  cc.TABLE_ID=c.TABLE_ID
  AND
  cc.COLUMN_NAME=c.COLUMN_NAME
  )
LEFT OUTER JOIN V_CATALOG.PRIMARY_KEYS p
  ON (
  cc.CONSTRAINT_ID=p.CONSTRAINT_ID
  AND
  cc.COLUMN_NAME=p.COLUMN_NAME
  )
LEFT OUTER JOIN V_CATALOG.COMMENTS cm
  ON (cc.CONSTRAINT_ID=cm.OBJECT_ID)
WHERE 1=1
  AND cc.CONSTRAINT_TYPE IN ('p', 'u')
  /*if isNotEmpty(catalogName)*/
  AND tc.constraint_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND tc.constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND tc.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND tc.constraint_name IN /*constraintName*/('%')
  /*end*/
ORDER BY cc.TABLE_SCHEMA, cc.TABLE_NAME, cc.CONSTRAINT_NAME, p.ORDINAL_POSITION, c.ORDINAL_POSITION
