SELECT
  O.*
, P.*
FROM ALL_OBJECTS O
INNER JOIN ALL_PROCEDURES P
  ON(O.OWNER=P.OWNER
  AND O.OBJECT_NAME=P.OBJECT_NAME
  AND P.PROCEDURE_NAME IS NULL)
WHERE O.OBJECT_TYPE IN ('PROCEDURE') 
  /*if isNotEmpty(schemaName)*/
  AND O.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(objectType)*/
  AND O.OBJECT_TYPE IN /*objectType*/('%')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND O.OBJECT_NAME IN /*objectName*/('%')
  /*end*/
ORDER BY O.OWNER, O.OBJECT_NAME
