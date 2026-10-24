SELECT a.SCHEMA AS schema_name, r.ROUTINENAME AS object_name, a.*
FROM SYSCAT.ROUTINEAUTH a
JOIN SYSCAT.ROUTINES r ON r.ROUTINESCHEMA=a.SCHEMA AND r.SPECIFICNAME=a.SPECIFICNAME
WHERE a.GRANTOR <> 'SYSIBM' AND a.EXECUTEAUTH IN ('Y', 'G') AND a.ROUTINETYPE IN ('F', 'P')
/*if isNotEmpty(schemaName)*/
  AND rtrim(a.SCHEMA) IN /*schemaName*/('%')
/*end*/
/*if isNotEmpty(objectName)*/
  AND rtrim(r.ROUTINENAME) IN /*objectName*/('%')
/*end*/
ORDER BY a.SCHEMA, r.ROUTINENAME, a.GRANTEE, a.GRANTOR
WITH UR
