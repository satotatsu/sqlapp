SELECT
  t.TABLE_CATALOG
, t.TABLE_SCHEMA
, t.TABLE_NAME
, t.REMARKS
, NULL AS "SQL"
FROM INFORMATION_SCHEMA.TABLES t
WHERE t.STORAGE_TYPE='TABLE LINK'
  /*if isNotEmpty(catalogName)*/
  AND t.TABLE_CATALOG IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.TABLE_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY t.TABLE_CATALOG, t.TABLE_SCHEMA, t.TABLE_NAME
