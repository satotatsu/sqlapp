SELECT t.CREATOR AS SCHEMA_NAME, t.NAME AS TABLE_NAME, t.REMARKS FROM SYSIBM.SYSTABLES t WHERE t.TYPE='T'
/*if isNotEmpty(schemaName)*/
 AND RTRIM(t.CREATOR) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
 AND RTRIM(t.NAME) IN /*tableName*/('%')
/*end*/
ORDER BY t.CREATOR, t.NAME
