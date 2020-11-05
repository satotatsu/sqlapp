SELECT *
FROM /*$dbaOrAll;length=3*/ALL_TRIGGERS
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND OWNER IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND TRIGGER_NAME IN /*triggerName*/('%')
  /*end*/
ORDER BY OWNER, TRIGGER_NAME