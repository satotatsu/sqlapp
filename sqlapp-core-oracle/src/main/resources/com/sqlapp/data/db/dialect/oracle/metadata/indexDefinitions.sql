SELECT A.OWNER, A.INDEX_NAME,
 DBMS_METADATA.GET_DDL('INDEX', A.INDEX_NAME, A.OWNER) AS INDEX_DEFINITION
FROM ALL_INDEXES A
WHERE 1=1
 /*if isNotEmpty(schemaName)*/
 AND A.OWNER IN /*schemaName*/('%')
 /*end*/
 /*if isNotEmpty(indexName)*/
 AND A.INDEX_NAME IN /*indexName*/('%')
 /*end*/
ORDER BY A.OWNER, A.INDEX_NAME
