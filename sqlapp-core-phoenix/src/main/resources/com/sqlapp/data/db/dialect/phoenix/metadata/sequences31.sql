SELECT TENANT_ID AS catalog_name, SEQUENCE_SCHEMA AS schema_name,
 SEQUENCE_NAME AS sequence_name, START_WITH AS start_with,
 INCREMENT_BY AS increment_by, CACHE_SIZE AS cache_size,
 MIN_VALUE AS min_value, MAX_VALUE AS max_value, CYCLE_FLAG AS cycle_flag
FROM SYSTEM."SEQUENCE"
WHERE 1=1
/*if isNotEmpty(catalogName) */
 AND TENANT_ID IN /*catalogName;type=VARCHAR*/('tenant')
/*end*/
/*if isEmpty(catalogName) */
 AND TENANT_ID IS NULL
/*end*/
/*if isNotEmpty(schemaName) */
 AND SEQUENCE_SCHEMA IN /*schemaName;type=VARCHAR*/('schema')
/*end*/
/*if isNotEmpty(sequenceName) */
 AND SEQUENCE_NAME IN /*sequenceName;type=VARCHAR*/('sequence')
/*end*/
ORDER BY TENANT_ID, SEQUENCE_SCHEMA, SEQUENCE_NAME
