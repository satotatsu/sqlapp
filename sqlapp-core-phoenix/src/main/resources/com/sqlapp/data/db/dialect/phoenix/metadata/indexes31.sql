SELECT TENANT_ID AS catalog_name, TABLE_SCHEM AS schema_name,
 TABLE_NAME AS index_name, DATA_TABLE_NAME AS table_name,
 SALT_BUCKETS AS salt_buckets, INDEX_STATE AS index_state,
 INDEX_TYPE AS index_type
FROM SYSTEM.CATALOG
WHERE TABLE_TYPE='i' AND COLUMN_NAME IS NULL AND COLUMN_FAMILY IS NULL
/*if isNotEmpty(catalogName) */
 AND TENANT_ID IN /*catalogName;type=VARCHAR*/('tenant')
/*end*/
/*if isEmpty(catalogName) */
 AND TENANT_ID IS NULL
/*end*/
/*if isNotEmpty(schemaName) */
 AND TABLE_SCHEM IN /*schemaName;type=VARCHAR*/('schema')
/*end*/
/*if isNotEmpty(tableName) */
 AND DATA_TABLE_NAME IN /*tableName;type=VARCHAR*/('table')
/*end*/
/*if isNotEmpty(indexName) */
 AND TABLE_NAME IN /*indexName;type=VARCHAR*/('index')
/*end*/
ORDER BY TENANT_ID, TABLE_SCHEM, TABLE_NAME
