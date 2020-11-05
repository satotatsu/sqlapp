SELECT
  S.*
, O.*
FROM /*$dbaOrAll;length=3*/ALL_SYNONYMS S
INNER JOIN ALL_OBJECTS O
ON (S.OWNER=O.OWNER
    AND
    S.SYNONYM_NAME=O.OBJECT_NAME)
WHERE 0=0 
  /*if isNotEmpty(schemaName)*/
  AND S.OWNER IN /*schemaName*/('%')
  /*end*/
  AND S.OWNER NOT IN ('PUBLIC')
  /*if isNotEmpty(synonymName)*/
  AND S.SYNONYM_NAME IN /*synonymName*/('%')
  /*end*/
ORDER BY S.OWNER, S.SYNONYM_NAME