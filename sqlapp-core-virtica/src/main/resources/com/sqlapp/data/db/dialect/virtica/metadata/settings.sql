SELECT
  p.parameter_name
, p.current_value
, p.default_value
FROM V_MONITOR.CONFIGURATION_PARAMETERS p
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND p.parameter_name IN /*settingName*/('%')
  /*end*/
ORDER BY p.parameter_name
