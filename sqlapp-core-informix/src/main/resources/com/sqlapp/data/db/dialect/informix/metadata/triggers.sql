SELECT
  DBINFO('dbname') AS catalog_name
, tr.owner AS schema_name
, tr.trigid AS trigger_id
, tr.trigname AS trigger_name
, tr.event AS trigger_event
, tr.old AS old_reference
, tr.new AS new_reference
, t.owner AS table_schema_name
, t.tabname AS table_name
, b.datakey AS data_key
, b.data AS trigger_text
FROM systriggers tr
INNER JOIN systables t
  ON (tr.tabid = t.tabid)
LEFT JOIN systrigbody b
  ON (tr.trigid = b.trigid AND b.datakey IN ('D', 'A'))
WHERE 1 = 1
  /*if isNotEmpty(schemaName)*/
  AND tr.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(triggerName)*/
  AND tr.trigname IN /*triggerName*/('%')
  /*end*/
ORDER BY tr.owner, tr.trigname, b.datakey DESC, b.seqno
