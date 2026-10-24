SELECT SEC$USER AS grantee, SEC$USER_TYPE AS grantee_type
FROM SEC$DB_CREATORS
WHERE 1 = 1
  /*if isNotEmpty(userName) */
  AND SEC$USER IN /*userName*/('%')
  /*end*/
ORDER BY SEC$USER, SEC$USER_TYPE
