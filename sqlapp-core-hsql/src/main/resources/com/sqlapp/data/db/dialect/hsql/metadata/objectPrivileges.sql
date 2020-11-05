SELECT *
FROM
(
	SELECT
	  rtp.grantor
	, rtp.grantee
	, rtp.table_catalog AS catalog_name
	, rtp.table_schema AS schema_name
	, rtp.table_name AS object_name
	, rtp.privilege_type
	, rtp.is_grantable
	, rtp.with_hierarchy
	FROM information_schema.role_table_grants rtp
	WHERE 1=1
	  /*if isNotEmpty(catalogName)*/
	  AND rtp.table_catalog IN /*catalogName*/('%')
	  /*end*/
	  /*if isNotEmpty(schemaName)*/
	  AND rtp.table_schema IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(objectName)*/
	  AND rtp.table_name IN /*objectName*/('%')
	  /*end*/
	UNION
	SELECT
	  rug.grantor
	, rug.grantee
	, rug.object_catalog AS catalog_name
	, rug.object_schema AS schema_name
	, rug.object_name AS object_name
	, rug.privilege_type
	, rug.is_grantable
	, 'NO' AS with_hierarchy
	FROM information_schema.role_usage_grants rug
	WHERE 1=1
	  /*if isNotEmpty(catalogName)*/
	  AND rug.object_catalog IN /*catalogName*/('%')
	  /*end*/
	  /*if isNotEmpty(schemaName)*/
	  AND rug.object_schema IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(objectName)*/
	  AND rug.udt_name IN /*objectName*/('%')
	  /*end*/
)
ORDER BY grantor, grantee, catalog_name, schema_name, object_name
