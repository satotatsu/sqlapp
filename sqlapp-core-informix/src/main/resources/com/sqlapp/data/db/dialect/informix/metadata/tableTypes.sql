SELECT TRIM(DBINFO('dbname')) AS catalog_name, TRIM(t.owner) AS schema_name,
 t.tabname AS table_name, t.type_xid AS row_type_id, x.name AS row_type_name, TRIM(x.owner) AS row_type_owner,
 p.tabname AS parent_name, TRIM(p.owner) AS parent_owner
FROM systables t
LEFT JOIN sysxtdtypes x ON x.extended_id=t.type_xid
LEFT JOIN sysinherits h ON h.child=t.tabid AND h.class='T'
LEFT JOIN systables p ON p.tabid=h.parent
WHERE t.tabtype='T'
 /*if isNotEmpty(schemaName)*/ AND t.owner IN /*schemaName*/('%') /*end*/
 /*if isNotEmpty(tableName)*/ AND t.tabname IN /*tableName*/('%') /*end*/
ORDER BY t.owner,t.tabname
