SELECT t.TABLE_SCHEMA AS SCHEMA_NAME, t.TABLE_NAME, t.TABLE_TEXT AS REMARKS FROM QSYS2.SYSTABLES t WHERE t.TABLE_TYPE='T'
/*if isNotEmpty(schemaName)*/
 AND RTRIM(t.TABLE_SCHEMA) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(tableName)*/
 AND RTRIM(t.TABLE_NAME) IN /*tableName*/('%')
/*end*/
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
