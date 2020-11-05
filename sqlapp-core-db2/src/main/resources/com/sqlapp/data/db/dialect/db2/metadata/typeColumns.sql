SELECT *
FROM
(
	SELECT
	    a.TYPESCHEMA AS schema_name
	  , a.TYPENAME AS type_name
	  , a.ATTR_NAME AS column_name
	  , a.ATTR_TYPENAME AS data_type
	  , a.*
	FROM SYSCAT.ATTRIBUTES a
	WHERE 1=1
	  /*if isNotEmpty(schemaName) */
	  AND rtrim(a.TYPESCHEMA) IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(typeName) */
	  AND rtrim(a.TYPENAME) IN /*typeName*/('%')
	  /*end*/
	UNION ALL
	SELECT
	    a.TYPESCHEMA AS schema_name
	  , a.TYPENAME AS type_name
	  , a.FIELDNAME AS column_name
	  , a.FIELDTYPENAME AS data_type
	  , a.LENGTH
	  , a.SCALE
	  , a.CODEPAGE
	  , a.COLLATIONSCHEMA
	  , a.COLLATIONNAME
	  , null AS LOGGED
	  , null AS COMPACT
	  , a.ORDINAL
	FROM SYSCAT.ROWFIELDS a
	WHERE 1=1
	  /*if isNotEmpty(schemaName) */
	  AND rtrim(a.TYPESCHEMA) IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(typeName) */
	  AND rtrim(a.TYPENAME) IN /*typeName*/('%')
	  /*end*/
) a
ORDER BY a.schema_name, a.type_name, a.ORDINAL
WITH UR
