SELECT *
FROM
(
	--module
	SELECT 
	    m.MODULESCHEMA AS schema_name
	  , m.MODULENAME AS object_name
	  , m.BASE_MODULESCHEMA AS base_schema
	  , m.BASE_MODULENAME AS base_object
	  , m.CREATE_TIME
	  , m.REMARKS
	FROM SYSCAT.MODULES m
	WHERE 1=1
	  AND m.MODULETYPE='A'
	  /*if isNotEmpty(schemaName)*/
	  AND rtrim(m.MODULESCHEMA) IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(tableName)*/
	  AND rtrim(m.MODULENAME) IN /*tableName*/('%')
	  /*end*/
    --sequence
	UNION ALL
	SELECT
	    s.SEQSCHEMA AS schema_name
	  , s.SEQNAME AS object_name
	  , s.BASE_SEQSCHEMA AS base_schema
	  , s.BASE_SEQNAME AS base_object
	  , s.CREATE_TIME
	  , s.REMARKS
	FROM SYSCAT.SEQUENCES s
	WHERE 1=1
	  AND s.SEQTYPE='A'
	  /*if isNotEmpty(schemaName) */
	  AND rtrim(s.SEQSCHEMA) IN /*schemaName*/('%')
	  /*end*/
	  /*if isNotEmpty(sequenceName) */
	  AND rtrim(s.SEQNAME) IN /*synonymName*/('%')
	  /*end*/
	--table
	UNION ALL
	SELECT
	    t.TABSCHEMA AS schema_name
	  , t.TABNAME AS object_name
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
	  /*if isNotEmpty(tableName)*/
	  AND rtrim(t.TABNAME) IN /*tableName*/('%')
	  /*end*/
)
ORDER BY schema_name, object_name
WITH UR
