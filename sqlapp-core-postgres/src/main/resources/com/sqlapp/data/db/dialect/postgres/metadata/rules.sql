SELECT
  current_database() AS rule_catalog
, r.*
FROM pg_catalog.pg_rules r
WHERE 0=0
  /*if isNotEmpty(schemaName)*/
  AND r.schemaname = /*schemaName*/'public'
  /*end*/
  /*if isNotEmpty(ruleName)*/
  AND r.rulename = /*ruleName*/''
  /*end*/
ORDER BY r.schemaname, r.rulename