SELECT V.*
  , C.COMMENT AS REMARKS
FROM information_schema.views V
LEFT OUTER JOIN information_schema.SYSTEM_COMMENTS C
  ON (
    V.TABLE_CATALOG=C.OBJECT_CATALOG
    AND V.TABLE_SCHEMA=C.OBJECT_SCHEMA
    AND V.TABLE_NAME=C.OBJECT_NAME
    AND C.OBJECT_TYPE='VIEW'
    AND C.COLUMN_NAME IS NULL
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
ORDER BY table_catalog, table_schema, table_name