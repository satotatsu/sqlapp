SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(s.owner) AS schema_name
, s.tabname AS object_name
, CASE WHEN st.btabid>0 THEN NULL ELSE TRIM(st.dbname) END AS base_catalog
, COALESCE(TRIM(b.owner), TRIM(st.owner)) AS base_schema
, COALESCE(b.tabname, st.tabname) AS base_object
, s.tabtype AS synonym_type
, TRIM(st.servername) AS server_name
, d.is_ansi AS ansi_database
FROM systables s
INNER JOIN syssyntable st
  ON (s.tabid = st.tabid)
LEFT OUTER JOIN systables b
  ON (st.btabid = b.tabid)
INNER JOIN sysmaster:sysdatabases d ON d.name=DBINFO('dbname')
WHERE s.tabtype IN ('P', 'S')
  /*if isNotEmpty(schemaName)*/
  AND s.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(synonymName)*/
  AND s.tabname IN /*synonymName*/('%')
  /*end*/
ORDER BY s.owner, s.tabname
