SELECT
c.*
FROM MON$CONTEXT_VARIABLES c
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND MON$VARIABLE_NAME IN /*settingName*/('%')
  /*end*/
ORDER BY MON$VARIABLE_NAME
