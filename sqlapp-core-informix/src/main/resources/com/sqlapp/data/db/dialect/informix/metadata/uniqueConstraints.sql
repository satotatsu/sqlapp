SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, i.idxname AS index_name
, TRIM(i.owner) AS index_owner
, i.idxtype AS index_kind
, i.indexkeys::LVARCHAR AS raw_index_keys
, i.clustered AS is_clustered
, i.amid AS access_method
, i.amparam AS access_parameters
, am.am_name AS access_method_name
, TRIM(am.am_owner) AS access_method_owner
, con.constrname AS constraint_name
FROM systables t
INNER JOIN sysindices i ON i.tabid=t.tabid
LEFT JOIN sysams am ON am.am_id=i.amid
INNER JOIN sysconstraints con ON con.tabid=t.tabid AND con.idxname=i.idxname AND con.constrtype='U'
WHERE t.tabtype='T'
  /*if isNotEmpty(catalogName)*/
  AND DBINFO('dbname') IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
ORDER BY t.owner,t.tabname,i.idxname
