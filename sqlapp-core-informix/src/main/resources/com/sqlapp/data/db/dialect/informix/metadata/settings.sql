SELECT cf_id AS setting_id, cf_name AS setting_name, cf_effective AS effective_value
, cf_original AS boot_value, cf_default AS default_value
FROM sysmaster:"informix".sysconfig
WHERE 1 = 1
/*if isNotEmpty(settingName)*/
  AND cf_name IN /*settingName*/('%')
/*end*/
ORDER BY cf_name
