SELECT
  A.*
FROM ALL_DB_LINKS A
WHERE 1=1
  AND A.OWNER = 'PUBLIC'
  /*if isNotEmpty(dbLinkName)*/
  AND A.DB_LINK IN /*dbLinkName*/('%')
  /*end*/
ORDER BY A.OWNER, A.DB_LINK
