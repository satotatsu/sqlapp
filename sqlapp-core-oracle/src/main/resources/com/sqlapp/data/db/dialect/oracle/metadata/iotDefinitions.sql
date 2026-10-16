SELECT A.OWNER, A.TABLE_NAME,
 DBMS_METADATA.GET_DDL('TABLE', A.TABLE_NAME, A.OWNER) AS TABLE_DEFINITION
FROM ALL_TABLES A
WHERE A.IOT_TYPE='IOT'
 /*if isNotEmpty(schemaName)*/
 AND A.OWNER IN /*schemaName*/('%')
 /*end*/
 /*if isNotEmpty(tableName)*/
 AND A.TABLE_NAME IN /*tableName*/('%')
 /*end*/
ORDER BY A.OWNER, A.TABLE_NAME
