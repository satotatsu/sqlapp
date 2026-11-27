SELECT TENANT_ID AS catalog_name, TABLE_SCHEM AS schema_name,
 TABLE_NAME AS index_name, COLUMN_NAME AS column_name,
 COLUMN_FAMILY AS column_family, ORDINAL_POSITION AS ordinal_position,
 SORT_ORDER AS sort_order
FROM SYSTEM.CATALOG
WHERE DATA_TABLE_NAME IS NOT NULL AND COLUMN_NAME IS NOT NULL
 AND TABLE_NAME IN /*indexNames;type=VARCHAR*/('index')
/*if isNotEmpty(catalogName) */
 AND TENANT_ID IN /*catalogName;type=VARCHAR*/('tenant')
/*end*/
/*if isEmpty(catalogName) */
 AND TENANT_ID IS NULL
/*end*/
/*if isNotEmpty(schemaName) */
 AND TABLE_SCHEM IN /*schemaName;type=VARCHAR*/('schema')
/*end*/
ORDER BY TENANT_ID, TABLE_SCHEM, TABLE_NAME, ORDINAL_POSITION
