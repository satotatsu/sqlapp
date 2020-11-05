SELECT
  A.*
FROM ALL_DB_LINKS A
WHERE 1=1 
  AND A.OWNER NOT IN ('PUBLIC')
  /*if isNotEmpty(schemaName)*/
  AND A.OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(dbLinkName)*/
  AND A.DB_LINK IN /*dbLinkName*/('%')
  /*end*/
ORDER BY A.OWNER, A.DB_LINK
