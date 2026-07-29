SELECT
  t.TABLE_CATALOG
, t.TABLE_SCHEMA
, t.TABLE_NAME
, t.TABLE_TYPE
, t.STORAGE_TYPE
, t.REMARKS
, t.ROW_COUNT_ESTIMATE
FROM INFORMATION_SCHEMA.TABLES t
WHERE t.TABLE_TYPE IN (
    'BASE TABLE'
  , 'GLOBAL TEMPORARY'
  , 'LOCAL TEMPORARY'
)
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
