SELECT
   *
FROM SYSCAT.CONTROLS c
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(c.CONTROLSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(maskName)*/
  AND rtrim(c.CONTROLNAME) IN /*maskName*/('%')
  /*end*/
  AND c.CONTROLTYPE='C'
ORDER BY c.CONTROLSCHEMA, c.CONTROLNAME
WITH UR
