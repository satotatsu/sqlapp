SELECT
    A.*
FROM ALL_DIRECTORIES A
WHERE 1=1
  /*if isNotEmpty(directoryName)*/
  AND A.DIRECTORY_NAME IN /*directoryName*/('%')
  /*end*/
ORDER BY A.OWNER, A.DIRECTORY_NAME
