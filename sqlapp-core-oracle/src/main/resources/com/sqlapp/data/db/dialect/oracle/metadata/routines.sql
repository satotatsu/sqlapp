SELECT
  O.*
FROM ALL_OBJECTS O
WHERE O.OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY', 'PROCEDUDE', 'FUNCTION') 
  /*if isNotEmpty(schemaName)*/
  AND O.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectType)*/
  AND O.OBJECT_TYPE IN /*objectType*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND O.OBJECT_NAME IN /*objectName*/('%')
  /*end*/
ORDER BY OWNER, OBJECT_NAME
