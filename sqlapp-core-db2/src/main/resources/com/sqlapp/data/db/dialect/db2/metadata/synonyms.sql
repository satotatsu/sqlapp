	SELECT
	    t.TABSCHEMA AS schema_name
	  , t.TABNAME AS object_name
	  , 'TABLE' AS alias_type
	  , t.BASE_TABSCHEMA AS base_schema
	  , t.BASE_TABNAME AS base_object
	  , t.CREATE_TIME
	  , t.REMARKS
	FROM SYSCAT.TABLES t
	WHERE 1=1
	  AND t.TYPE IN ('A')
	  /*if isNotEmpty(schemaName)*/
	  AND rtrim(t.TABSCHEMA) IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(synonymName)*/
	  AND rtrim(t.TABNAME) IN /*synonymName*/('%')
	  /*end*/
ORDER BY schema_name, object_name
WITH UR
