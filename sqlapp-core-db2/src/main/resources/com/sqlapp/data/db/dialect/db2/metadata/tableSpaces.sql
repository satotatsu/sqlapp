SELECT
      A.*
    , TBSPACE AS TABLE_SPACE
FROM SYSCAT.TABLESPACES A
WHERE 1=1 
  /*if isNotEmpty(tableSpaceName)*/
  AND A.TBSPACE IN /*tableSpaceName*/('%')
  /*end*/
ORDER BY A.TBSPACE
