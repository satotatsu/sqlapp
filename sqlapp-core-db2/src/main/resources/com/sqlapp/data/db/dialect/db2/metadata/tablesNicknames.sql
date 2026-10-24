SELECT
    t.TABSCHEMA AS schema_name
  , t.TABNAME AS table_name
  , t.TBSPACE AS tablespace_name
  , t.INDEX_TBSPACE AS index_tablespace_name
  , t.LONG_TBSPACE AS lob_tablespace_name
  , t.*
  , n.SERVERNAME AS nickname_server
  , n.REMOTE_SCHEMA AS nickname_remote_schema
  , n.REMOTE_TABLE AS nickname_remote_table
  , n.CACHINGALLOWED AS nickname_caching
  , w.WRAPTYPE AS nickname_wrapper_type
FROM SYSCAT.TABLES t
LEFT JOIN SYSCAT.NICKNAMES n ON n.TABSCHEMA=t.TABSCHEMA AND n.TABNAME=t.TABNAME
LEFT JOIN SYSCAT.SERVERS s ON s.SERVERNAME=n.SERVERNAME
LEFT JOIN SYSCAT.WRAPPERS w ON w.WRAPNAME=s.WRAPNAME
WHERE 1=1
  /*if isNotEmpty(schemaName)*/
  AND rtrim(t.TABSCHEMA) IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName)*/
  AND rtrim(t.TABNAME) IN /*tableName*/('%')
  /*end*/
  AND t.TYPE IN ('L', 'T', 'U', 'N')
ORDER BY t.TABSCHEMA, t.TABNAME
WITH UR

