SELECT *
FROM ALL_SOURCE
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND OWNER IN /*schemaName*/('SYSTEM')
  /*end*/
  /*if isNotEmpty(objectType)*/
  AND TYPE IN /*objectType*/('FUNCTION', 'JAVA SOURCE', 'PACKAGE', 'PACKAGE BODY', 'PROCEDURE', 'TRIGGER', 'TYPE', 'TYPE BODY')
  /*end*/
  /*if isNotEmpty(objectName)*/
  AND NAME IN /*objectName*/('APEXWS')
  /*end*/
ORDER BY OWNER, TYPE, NAME, LINE
