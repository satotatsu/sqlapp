SELECT *
FROM information_schema.triggers
WHERE 1=1
  /*if isNotEmpty(catalogName) */
  AND trigger_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND trigger_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND trigger_name IN /*triggerName*/('%')
  /*end*/
ORDER BY trigger_catalog, trigger_schema, trigger_name