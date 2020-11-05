SELECT
  U.*
FROM /*$dbaOrAll;length=3*/ALL_USERS U
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND USERNAME IN /*schemaName*/('%')
  /*end*/
ORDER BY USERNAME
