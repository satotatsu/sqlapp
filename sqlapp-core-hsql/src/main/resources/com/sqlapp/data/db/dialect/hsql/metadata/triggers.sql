SELECT *
FROM information_schema.triggers
WHERE true
  /*if isNotEmpty(schemaName) */
  AND trigger_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND trigger_name IN /*triggerName*/('%')
  /*end*/
