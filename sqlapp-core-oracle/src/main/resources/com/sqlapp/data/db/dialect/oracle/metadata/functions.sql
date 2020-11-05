SELECT
  O.*
, A.*
, P.*
FROM ALL_OBJECTS O
INNER JOIN ALL_PROCEDURES P
  ON(O.OWNER=P.OWNER
  AND O.OBJECT_NAME=P.OBJECT_NAME)
INNER JOIN ALL_ARGUMENTS A
  ON (O.OWNER=A.OWNER
  AND O.OBJECT_NAME=A.OBJECT_NAME
  AND A.ARGUMENT_NAME IS NULL
  AND A.DATA_LEVEL=0
  )
WHERE O.OBJECT_TYPE IN ('FUNCTION') 
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
