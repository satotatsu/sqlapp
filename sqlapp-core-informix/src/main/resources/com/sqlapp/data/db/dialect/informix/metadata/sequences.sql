SELECT
  DBINFO('dbname') AS catalog_name
, t.owner AS schema_name
, t.tabname AS sequence_name
, s.start_val AS start_value
, s.inc_val AS increment_value
, s.min_val AS minimum_value
, s.max_val AS maximum_value
, s.cycle AS cycle_option
, s.cache AS cache_size
FROM syssequences s
INNER JOIN systables t
  ON (s.tabid = t.tabid)
WHERE 1 = 1
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(sequenceName)*/
  AND t.tabname IN /*sequenceName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname
