SELECT 
  t.TABLE_SCHEMA AS constraint_schema
, 'INTERLEAVE' AS constraint_schema
, t.TABLE_NAME
, t.ON_DELETE_ACTION AS delete_rule
, parent.TABLE_SCHEMA AS referenced_table_schema
, parent.TABLE_NAME AS referenced_table_name
, i.COLUMN_NAME
, pic.COLUMN_NAME AS referenced_column_name
FROM INFORMATION_SCHEMA.TABLES t
INNER JOIN INFORMATION_SCHEMA.INDEXES i
  ON
  (
     t.TABLE_SCHEMA=i.TABLE_SCHEMA
     AND 
     t.TABLE_NAME=i.TABLE_NAME
     AND
     i.INDEX_TYPE='PRIMARY_KEY'
  )
INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS ic
  ON
  (
     i.TABLE_SCHEMA=ic.TABLE_SCHEMA
     AND 
     i.TABLE_NAME=ic.TABLE_NAME
     AND 
     i.INDEX_NAME=ic.INDEX_NAME
  )
INNER JOIN INFORMATION_SCHEMA.TABLES parent
  ON
  (
     t.PARENT_TABLE_NAME=parent.TABLE_NAME
     AND
     t.TABLE_SCHEMA=parent.TABLE_SCHEMA
  )
INNER JOIN INFORMATION_SCHEMA.INDEXES pi
  ON
  (
     p.TABLE_SCHEMA=pi.TABLE_SCHEMA
     AND 
     p.TABLE_NAME=pi.TABLE_NAME
     AND
     p.INDEX_TYPE='PRIMARY_KEY'
  )
INNER JOIN INFORMATION_SCHEMA.INDEX_COLUMNS pic
  ON
  (
     ic.TABLE_SCHEMA=pic.TABLE_SCHEMA
     AND 
     ic.TABLE_NAME=pic.TABLE_NAME
     AND 
     ic.INDEX_NAME=pic.INDEX_NAME
     AND 
     ic.COLUMN_NAME=pic.COLUMN_NAME
  )
WHERE TRUE
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND 'INTERLEAVE' IN /*constraintName*/('%')
  /*end*/
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME, ic.ORDINAL_POSITION

