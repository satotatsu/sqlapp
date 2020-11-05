SELECT
  p.*
FROM V$SYSTEM_PARAMETER p
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND NAME IN /*settingName*/('%')
  /*end*/
ORDER BY NAME, NUM