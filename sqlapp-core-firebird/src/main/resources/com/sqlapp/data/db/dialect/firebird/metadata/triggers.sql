SELECT
T.*
FROM RDB$TRIGGERS T
WHERE RDB$SYSTEM_FLAG=0
  AND T.RDB$TRIGGER_NAME NOT IN
  (
  	  SELECT RDB$TRIGGER_NAME
  	  FROM RDB$CHECK_CONSTRAINTS
  )
  /*if isNotEmpty(triggerName) */
  AND RDB$TRIGGER_NAME IN /*triggerName*/('%')
  /*end*/
  /*if readerOptions.excludeSystemObjects */
  AND T.RDB$SYSTEM_FLAG=0
  /*end*/
ORDER BY RDB$TRIGGER_NAME
