SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, i.idxname AS index_name
, TRIM(i.owner) AS index_owner
, i.idxtype AS index_kind
, s.state AS index_state
, i.indexkeys::LVARCHAR AS raw_index_keys
, i.clustered AS is_clustered
, i.amid AS access_method
, i.amparam AS access_parameters
, am.am_name AS access_method_name
, TRIM(am.am_owner) AS access_method_owner
, TRIM(am.am_sptype) AS access_method_storage
, sp.is_sbspace AS storage_is_sbspace
, sp.is_blobspace AS storage_is_blobspace
, f.indexname AS storage_index_name
, f.strategy AS storage_strategy
, f.partition AS storage_partition
, f.exprtext AS storage_expression
, f.dbspace AS storage_dbspace
, f.evalpos AS storage_position
FROM systables t
INNER JOIN sysindices i ON i.tabid=t.tabid
LEFT JOIN sysams am ON am.am_id=i.amid
LEFT JOIN sysfragments f ON f.fragtype='I' AND f.tabid=i.tabid AND f.indexname=i.idxname
LEFT JOIN sysmaster:sysdbspaces sp ON sp.name=f.dbspace
LEFT JOIN sysobjstate s ON s.objtype='I' AND s.tabid=i.tabid AND s.name=i.idxname AND s.owner=i.owner
WHERE t.tabtype='T'
  AND NOT EXISTS (SELECT 1 FROM sysconstraints con WHERE con.tabid=i.tabid AND con.idxname=i.idxname AND con.constrtype IN ('P','U','R'))
  /*if isNotEmpty(catalogName)*/
  AND DBINFO('dbname') IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(indexName)*/
  AND i.idxname IN /*indexName*/('%')
  /*end*/
ORDER BY t.owner,t.tabname,i.idxname,f.evalpos
