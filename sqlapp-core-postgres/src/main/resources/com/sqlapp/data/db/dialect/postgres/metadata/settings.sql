SELECT s.*
FROM pg_settings s
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND s.name IN /*settingName*/('%')
  /*end*/
ORDER BY s.name