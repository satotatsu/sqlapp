SELECT
  e.*
, e.EVENT_SCHEMA AS schema_name
FROM
  INFORMATION_SCHEMA.EVENTS e
WHERE TRUE 
  /*if isNotEmpty(schemaName)*/
  AND e.EVENT_SCHEMA IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(eventName)*/
  AND e.EVENT_NAME IN /*eventName*/('%')
  /*end*/
ORDER BY e.EVENT_CATALOG, e.EVENT_SCHEMA, e.EVENT_NAME
