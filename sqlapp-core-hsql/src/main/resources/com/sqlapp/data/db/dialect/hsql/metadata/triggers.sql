SELECT T.*, C.EVENT_OBJECT_COLUMN
FROM information_schema.triggers T
LEFT JOIN information_schema.triggered_update_columns C
  ON T.TRIGGER_CATALOG = C.TRIGGER_CATALOG
 AND T.TRIGGER_SCHEMA = C.TRIGGER_SCHEMA
 AND T.TRIGGER_NAME = C.TRIGGER_NAME
WHERE true
  /*if isNotEmpty(catalogName) */
  AND T.trigger_catalog IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND T.trigger_schema IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName) */
  AND T.trigger_name IN /*triggerName*/('%')
  /*end*/
ORDER BY T.TRIGGER_CATALOG, T.TRIGGER_SCHEMA, T.ACTION_ORDER, T.TRIGGER_NAME, C.EVENT_OBJECT_COLUMN
