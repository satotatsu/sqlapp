SELECT
  S.*
, O.*
FROM /*$dbaOrAll;length=3*/ALL_SYNONYMS S
INNER JOIN ALL_OBJECTS O
ON (S.OWNER=O.OWNER
    AND
    S.SYNONYM_NAME=O.OBJECT_NAME)
WHERE 0=0 
  AND S.OWNER = 'PUBLIC'
  /*if isNotEmpty(synonymName)*/
  AND S.SYNONYM_NAME IN /*synonymName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND S.TABLE_OWNER IN /*schemaName*/('%')
  /*end*/
ORDER BY S.OWNER, S.SYNONYM_NAME