SELECT
    A.*
FROM DBA_DATA_FILES A
WHERE 1=1 
  /*if isNotEmpty(fileId)*/
  AND A.FILE_ID IN /*fileId*/('%')
  /*end*/
  /*if isNotEmpty(tableSpaceName)*/
  AND A.TABLESPACE_NAME IN /*tableSpaceName*/('%')
  /*end*/
ORDER BY A.TABLESPACE_NAME, A.FILE_ID
