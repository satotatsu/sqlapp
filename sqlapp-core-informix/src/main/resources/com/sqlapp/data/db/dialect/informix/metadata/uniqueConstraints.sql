SELECT
  TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, i.idxname AS index_name
, TRIM(i.owner) AS index_owner
, i.idxtype AS index_kind
, bs.state AS index_state
, i.indexkeys::LVARCHAR AS raw_index_keys
, i.clustered AS is_clustered
, i.amid AS access_method
, i.amparam AS access_parameters
, am.am_name AS access_method_name
, TRIM(am.am_owner) AS access_method_owner
, TRIM(am.am_sptype) AS access_method_storage
, sp.is_sbspace AS storage_is_sbspace
, sp.is_blobspace AS storage_is_blobspace
, con.constrname AS constraint_name
, CASE WHEN SUBSTR(i.idxname,1,1)=' ' THEN '0' ELSE '1' END AS backing_named
, f.indexname AS storage_index_name, f.strategy AS storage_strategy,
 f.partition AS storage_partition, f.exprtext AS storage_expression,
 f.dbspace AS storage_dbspace, f.evalpos AS storage_position
FROM systables t
INNER JOIN sysindices i ON i.tabid=t.tabid
LEFT JOIN sysams am ON am.am_id=i.amid
LEFT JOIN sysobjstate bs ON bs.objtype='I' AND bs.tabid=i.tabid AND bs.name=i.idxname AND bs.owner=i.owner
LEFT JOIN sysfragments f ON f.fragtype='I' AND f.tabid=i.tabid AND f.indexname=i.idxname
LEFT JOIN sysmaster:sysdbspaces sp ON sp.name=f.dbspace
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
ORDER BY t.owner,t.tabname,i.idxname,f.evalpos
