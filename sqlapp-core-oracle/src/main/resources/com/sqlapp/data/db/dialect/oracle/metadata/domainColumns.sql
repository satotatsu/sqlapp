SELECT C.* FROM ALL_DOMAIN_COLS C
WHERE C.DATA_TYPE_ID=1
 /*if isNotEmpty(schemaName)*/
 AND C.OWNER IN /*schemaName*/('%')
 /*end*/
 /*if isNotEmpty(domainName)*/
 AND C.DOMAIN_NAME IN /*domainName*/('%')
 /*end*/
ORDER BY C.OWNER, C.DOMAIN_NAME, C.COLUMN_ID, C.DATA_TYPE_ID
