SELECT
t.*
, c.CHARACTER_SET_NAME AS TABLE_CHARACTER_SET
FROM information_schema.tables t
LEFT JOIN information_schema.collations c ON c.COLLATION_NAME = t.TABLE_COLLATION
WHERE TRUE 
  /*if isNotEmpty(schemaName)*/
  AND t.table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.table_name IN /*tableName*/('%')
  /*end*/
ORDER BY t.table_schema, t.table_name
