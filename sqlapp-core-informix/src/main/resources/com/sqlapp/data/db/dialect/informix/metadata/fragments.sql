SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, f.strategy
, f.partition
, f.exprtext
, f.dbspace
, f.evalpos AS position
, f.flags AS fragment_flags
, f.colno AS rolling_fragment_limit
, f.nrows AS rolling_size
, f.npused AS rolling_size_unit
FROM systables t
INNER JOIN sysfragments f
  ON (t.tabid = f.tabid)
WHERE f.fragtype = 'T'
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner, t.tabname, f.evalpos
