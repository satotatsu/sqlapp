SELECT
  A.*
, B.COMMENTS
FROM ALL_TABLES A
LEFT OUTER JOIN ALL_TAB_COMMENTS B
ON (A.OWNER=B.OWNER
    AND
    A.TABLE_NAME=B.TABLE_NAME
    AND
    B.TABLE_TYPE='TABLE'
    AND
    B.COMMENTS IS NOT NULL)
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND A.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND A.TABLE_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY A.OWNER, A.TABLE_NAME
