SELECT 
  current_database() AS catalog_name
, A.*
FROM
(
	SELECT
	    t.grantor
	  , t.grantee
	  , t.table_schema AS schema_name
	  , t.table_name AS object_name
	  , t.privilege_type
	  , t.is_grantable
	  , t.with_hierarchy
	FROM information_schema.table_privileges t
	WHERE 1=1
	  /*if isNotEmpty(schemaName)*/
	  AND t.table_schema IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(tableName)*/
	  AND t.table_name IN /*tableName*/('%')
	  /*end*/
	UNION ALL
	SELECT
		  udt.grantor
		, udt.grantee
		, udt.object_schema AS schema_name
		, udt.object_name AS object_name
		, udt.privilege_type
		, udt.is_grantable
		, 'NO' AS with_hierarchy
	FROM information_schema.role_usage_grants udt
	WHERE 1=1
	  /*if isNotEmpty(schemaName)*/
	  AND udt.object_schema IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(tableName)*/
	  AND udt.object_name IN /*tableName*/('%')
	  /*end*/
) A
ORDER BY grantor, grantee, schema_name, object_name
