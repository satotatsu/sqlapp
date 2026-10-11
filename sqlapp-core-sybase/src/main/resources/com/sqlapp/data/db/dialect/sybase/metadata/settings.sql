SELECT
  cfg.config AS setting_id
, cfg.comment AS setting_name
, CASE WHEN cfg.value2 IS NULL
    THEN CONVERT(VARCHAR(255), cfg.value)
    ELSE cfg.value2 END AS configured_value
, CASE WHEN cur.value2 IS NULL
    THEN CONVERT(VARCHAR(255), cur.value)
    ELSE cur.value2 END AS current_value
, cur.status
FROM master.dbo.sysconfigures cfg
INNER JOIN master.dbo.syscurconfigs cur ON cfg.config=cur.config
WHERE 1=1
  /*if isNotEmpty(settingName) */
  AND cfg.comment IN /*settingName;type=VARCHAR*/('%')
  /*end*/
ORDER BY cfg.comment
