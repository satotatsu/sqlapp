SELECT
  A.*
  ,B.COMMENTS
FROM ALL_MVIEWS A
LEFT OUTER JOIN ALL_MVIEW_COMMENTS B
  ON (A.OWNER=B.OWNER
  AND A.MVIEW_NAME=B.MVIEW_NAME)
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND A.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND A.MVIEW_NAME IN /*tableName*/('%')
  /*end*/
ORDER BY A.OWNER, A.MVIEW_NAME
