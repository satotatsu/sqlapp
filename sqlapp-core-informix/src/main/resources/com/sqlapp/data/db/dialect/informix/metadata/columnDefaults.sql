SELECT TRIM(DBINFO('dbname')) AS catalog_name
, TRIM(t.owner) AS schema_name
, t.tabname AS table_name
, c.colname AS column_name
, t.tabtype AS object_type
, d.type AS default_kind
, d.default AS default_value
, c.colno AS column_number
, nn.constrname AS not_null_name
, TRIM(nn.owner) AS not_null_owner
, ns.state AS not_null_state
, pk.constrname AS primary_key_name
, ps.state AS primary_key_state
, pki.indexkeys::LVARCHAR AS primary_key_columns
FROM systables t
LEFT JOIN syscolumns c ON c.tabid=t.tabid
LEFT JOIN sysdefaults d ON d.tabid=c.tabid AND d.colno=c.colno AND d.class='T'
LEFT JOIN (sysconstraints nn INNER JOIN syscoldepend nd ON nd.constrid=nn.constrid AND nn.constrtype='N')
  ON nn.tabid=c.tabid AND nd.colno=c.colno
LEFT JOIN sysobjstate ns ON ns.objtype='C' AND ns.tabid=nn.tabid AND ns.name=nn.constrname AND ns.owner=nn.owner
LEFT JOIN sysconstraints pk ON pk.tabid=c.tabid AND pk.constrtype='P'
LEFT JOIN sysobjstate ps ON ps.objtype='C' AND ps.tabid=pk.tabid AND ps.name=pk.constrname AND ps.owner=pk.owner
LEFT JOIN sysindices pki ON pki.tabid=pk.tabid AND pki.idxname=pk.idxname
WHERE 1=1
  /*if isNotEmpty(catalogName)*/
  AND DBINFO('dbname') IN /*catalogName*/('%')
  /*end*/
  /*if isNotEmpty(schemaName)*/
  AND t.owner IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND t.tabname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName)*/
  AND (c.colname IN /*columnName*/('%') OR t.tabtype NOT IN ('T','V'))
  /*end*/
ORDER BY t.owner,t.tabname,c.colno
