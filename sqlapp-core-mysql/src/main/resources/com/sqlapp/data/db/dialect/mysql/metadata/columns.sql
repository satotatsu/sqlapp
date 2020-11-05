SELECT
c.*
, t.auto_increment
FROM information_schema.columns c
LEFT OUTER JOIN information_schema.tables t
  ON (c.table_catalog=t.table_catalog
  AND c.table_schema=t.table_schema
  AND c.table_name=t.table_name)
WHERE TRUE 
  /*if isNotEmpty(schemaName)*/
  AND c.table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND c.column_name IN /*columnName*/('%')
  /*end*/
ORDER BY c.table_schema, c.table_name, c.ordinal_position
