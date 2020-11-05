SELECT
c.*
, i.column_name
, i.is_generated
, i.asc_or_desc
FROM information_schema.constraints c
INNER JOIN information_schema.indexes i
  ON (c.table_catalog=i.table_catalog
  AND c.table_schema=i.table_schema
  AND c.table_name=i.table_name
  AND c.unique_index_name=i.index_name
  )
WHERE constraint_type IN ('UNIQUE', 'PRIMARY KEY')
  /*if isNotEmpty(catalogName)*/
  AND c.constraint_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND c.constraint_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(constraintName)*/
  AND c.constraint_name IN /*constraintName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND c.table_name IN /*tableName*/('%')
  /*end*/
ORDER BY c.constraint_catalog, c.constraint_schema, c.table_name, c.constraint_type, c.constraint_name, i.ordinal_position