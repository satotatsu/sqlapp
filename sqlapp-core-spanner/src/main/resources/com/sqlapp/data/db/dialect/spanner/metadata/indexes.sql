SELECT
, i.*
, cc.COLUMN_NAME
, cc.ORDINAL_POSITION
, cc.COLUMN_ORDERING
, cc.IS_NULLABLE
, cc.SPANNER_TYPE
FROM INFORMATION_SCHEMA.INDEXES i
INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS cc
  ON (i.TABLE_SCHEMA=cc.TABLE_SCHEMA
  AND
  i.TABLE_NAME=cc.TABLE_NAME
  AND
  i.INDEX_NAME=cc.INDEX_NAME
  )
WHERE 1=1
  AND i.INDEX_TYPE IN ('INDEX')
  /*if isNotEmpty(schemaName)*/
  AND i.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND i.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND i.INDEX_NAME IN /*constraintName*/('%')
  /*end*/
ORDER BY i.TABLE_SCHEMA, i.TABLE_NAME, i.INDEX_NAME,
  cc.ORDINAL_POSITION
