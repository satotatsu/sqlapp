SELECT
  g.*
FROM information_schema.global_variables g
WHERE 1=1
  /*if isNotEmpty(settingName)*/
  AND variable_name IN /*settingName*/('%')
  /*end*/
ORDER BY variable_name
