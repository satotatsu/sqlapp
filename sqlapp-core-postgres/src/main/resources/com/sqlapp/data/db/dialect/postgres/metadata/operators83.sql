SELECT
    current_database() AS catalog_name
  , CAST(o.oid AS VARCHAR) AS oid
  , o.*
  , n.nspname AS schema_name
  , CASE
      WHEN lt.typelem <> 0 AND lt.typlen = -1 THEN 'ARRAY'
      WHEN lt.typbasetype IN (1186) then /*interval*/
        CASE ((lt.typtypmod)>>16)
    	  WHEN 32767 then 'interval'
    	  WHEN 4 then 'interval year'
    	  WHEN 2 then 'interval month'
    	  WHEN 8 then 'interval day'
    	  WHEN 6 then 'interval year to month'
    	  WHEN 8 then 'interval day'
    	  WHEN 1024 then 'interval hour'
    	  WHEN 1032 then 'interval day to hour'
    	  WHEN 2048 then 'interval minute'
    	  WHEN 3072 then 'interval hour to minute'
    	  WHEN 3080 then 'interval day to minute'
    	  WHEN 4096 then 'interval second'
    	  WHEN 6144 then 'interval minute to second'
    	  WHEN 7168 then 'interval hour to second'
    	  WHEN 7176 then 'interval day to second'
    	  ELSE 'interval'
        END
    ELSE lt.typname
    END AS left_type
  , CASE
      WHEN rt.typelem <> 0 AND rt.typlen = -1 THEN 'ARRAY'
      WHEN rt.typbasetype IN (1186) then /*interval*/
        CASE ((rt.typtypmod)>>16)
    	  WHEN 32767 then 'interval'
    	  WHEN 4 then 'interval year'
    	  WHEN 2 then 'interval month'
    	  WHEN 8 then 'interval day'
    	  WHEN 6 then 'interval year to month'
    	  WHEN 8 then 'interval day'
    	  WHEN 1024 then 'interval hour'
    	  WHEN 1032 then 'interval day to hour'
    	  WHEN 2048 then 'interval minute'
    	  WHEN 3072 then 'interval hour to minute'
    	  WHEN 3080 then 'interval day to minute'
    	  WHEN 4096 then 'interval second'
    	  WHEN 6144 then 'interval minute to second'
    	  WHEN 7168 then 'interval hour to second'
    	  WHEN 7176 then 'interval day to second'
    	  ELSE 'interval'
        END
    ELSE rt.typname
    END AS right_type
  , CASE
      WHEN rest.typelem <> 0 AND rest.typlen = -1 THEN 'ARRAY'
      WHEN rest.typbasetype IN (1186) then /*interval*/
        CASE ((rest.typtypmod)>>16)
    	  WHEN 32767 then 'interval'
    	  WHEN 4 then 'interval year'
    	  WHEN 2 then 'interval month'
    	  WHEN 8 then 'interval day'
    	  WHEN 6 then 'interval year to month'
    	  WHEN 8 then 'interval day'
    	  WHEN 1024 then 'interval hour'
    	  WHEN 1032 then 'interval day to hour'
    	  WHEN 2048 then 'interval minute'
    	  WHEN 3072 then 'interval hour to minute'
    	  WHEN 3080 then 'interval day to minute'
    	  WHEN 4096 then 'interval second'
    	  WHEN 6144 then 'interval minute to second'
    	  WHEN 7168 then 'interval hour to second'
    	  WHEN 7176 then 'interval day to second'
    	  ELSE 'interval'
        END
    ELSE rest.typname
    END AS result_type
  , oprc.oprname AS oprcom_name       --演算子の交替子
  , oprn.oprname AS oprnegate_name    --演算子の否定子
/*
  , oprls.oprname AS oprlsortop_name
  , oprrs.oprname AS oprrsortop_name
  , oprlt.oprname AS oprltcmpop_name
  , oprgt.oprname AS oprltcmpop_name
*/
  , npcode.nspname AS code_function_schema
  , pcode.proname AS code_function_name
  , nprest.nspname AS rest_function_schema
  , prest.proname AS rest_function_name
  , npjoin.nspname AS join_function_schema
  , pjoin.proname AS join_function_name
FROM pg_catalog.pg_operator o
INNER JOIN pg_catalog.pg_namespace n
  ON (n.oid = o.oprnamespace)
INNER JOIN pg_catalog.pg_type lt
  ON (o.oprleft=lt.oid)
INNER JOIN pg_catalog.pg_type rt
  ON (o.oprright=rt.oid)
INNER JOIN pg_catalog.pg_type rest
  ON (o.oprresult=rest.oid)
LEFT OUTER JOIN pg_catalog.pg_operator oprc
  ON (o.oprcom=oprc.oid)
LEFT OUTER JOIN pg_catalog.pg_operator oprn
  ON (o.oprnegate=oprn.oid)
/*
LEFT OUTER JOIN pg_catalog.pg_operator oprls
  ON (o.oprlsortop=oprls.oid)
LEFT OUTER JOIN pg_catalog.pg_operator oprrs
  ON (o.oprrsortop=oprrs.oid)
LEFT OUTER JOIN pg_catalog.pg_operator oprlt
  ON (o.oprltcmpop=oprlt.oid)
LEFT OUTER JOIN pg_catalog.pg_operator oprgt
  ON (o.oprgtcmpop=oprgt.oid) 
*/
INNER JOIN pg_catalog.pg_proc pcode
  ON (o.oprcode=pcode.oid)
INNER JOIN pg_catalog.pg_namespace npcode
  ON (pcode.pronamespace=npcode.oid)
LEFT OUTER JOIN pg_catalog.pg_proc prest
  ON (o.oprrest=prest.oid)
LEFT OUTER JOIN pg_catalog.pg_namespace nprest
  ON (prest.pronamespace=nprest.oid)
LEFT OUTER JOIN pg_catalog.pg_proc pjoin
  ON (o.oprjoin=pjoin.oid)
LEFT OUTER JOIN pg_catalog.pg_namespace npjoin
  ON (pjoin.pronamespace=npjoin.oid)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(operatorName)*/
  AND o.oprname IN /*operatorName*/('%')
  /*end*/
  /*if isNotEmpty(id) */
  AND o.oid IN /*id*/(10)
  /*end*/
ORDER BY n.nspname, o.oprname