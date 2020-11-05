SELECT COL.*
, C.COMMENT
FROM information_schema.columns COL
LEFT OUTER JOIN INFORMATION_SCHEMA.SYSTEM_COMMENTS C
  ON (
    COL.TABLE_CATALOG=C.OBJECT_CATALOG
    AND COL.TABLE_SCHEMA=C.OBJECT_SCHEMA
    AND COL.TABLE_NAME=C.OBJECT_NAME
    AND COL.COLUMN_NAME=C.COLUMN_NAME
  )
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND table_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND table_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND table_name IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND column_name IN /*columnName*/('%')
  /*end*/
ORDER BY table_catalog, table_schema, table_name, ordinal_position
