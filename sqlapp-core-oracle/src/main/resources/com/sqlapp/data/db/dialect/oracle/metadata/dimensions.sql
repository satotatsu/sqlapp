SELECT
  A.*
FROM ALL_DIMENSIONS A
WHERE 1=1 
  /*if isNotEmpty(schemaName)*/
  AND A.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(dimensionName)*/
  AND A.DIMENSION_NAME IN /*dimensionName*/('%')
  /*end*/
ORDER BY A.OWNER, A.DIMENSION_NAME
