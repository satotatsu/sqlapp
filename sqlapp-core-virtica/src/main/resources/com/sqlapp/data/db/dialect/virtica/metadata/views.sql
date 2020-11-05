SELECT V.*
  , C.COMMENT AS REMARKS
FROM V_CATALOG.VIEWS v
LEFT OUTER JOIN V_CATALOG.COMMENTS c
  ON (v.TABLE_ID=c.OBJECT_ID)
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