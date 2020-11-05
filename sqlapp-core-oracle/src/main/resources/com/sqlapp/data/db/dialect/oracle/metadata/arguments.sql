SELECT
  O.*
FROM ALL_ARGUMENTS O
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND O.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND O.OBJECT_NAME IN /*objectName*/('%')
  /*end*/
  /*if isNotEmpty(packageName)*/
  AND O.PACKAGE_NAME IN /*packageName*/('%')
  /*end*/
ORDER BY OWNER, OBJECT_NAME, PACKAGE_NAME, POSITION
