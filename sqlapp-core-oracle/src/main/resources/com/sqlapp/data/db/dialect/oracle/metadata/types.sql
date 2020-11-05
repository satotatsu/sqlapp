SELECT
  T.*
, O.*
FROM ALL_TYPES T
INNER JOIN ALL_OBJECTS O
ON (T.OWNER=O.OWNER
    AND
    T.TYPE_NAME=O.OBJECT_NAME
    AND
    O.OBJECT_TYPE='TYPE')
WHERE T.TYPECODE='OBJECT' 
  /*if isNotEmpty(schemaName)*/
  AND T.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(typeName)*/
  AND T.TYPE_NAME IN /*typeName*/('%')
  /*end*/
ORDER BY T.OWNER, T.TYPE_NAME