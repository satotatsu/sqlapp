SELECT s.*
FROM INFORMATION_SCHEMA.SETTINGS s
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND s.name IN /*settingName*/('%')
  /*end*/
--  AND s.name NOT LIKE 'property.%'
ORDER BY s.name