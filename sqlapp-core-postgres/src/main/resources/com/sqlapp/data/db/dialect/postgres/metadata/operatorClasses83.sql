SELECT
    current_database() AS catalog_name
  , ocn.nspname AS schema_name
  , oc.opcname AS operator_class_name
  , oc.opcdefault AS operator_default
  , am.amname as index_type
  , CASE
      WHEN datt.typbasetype IN (1186) then /*interval*/
        CASE ((datt.typtypmod)>>16)
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
    ELSE datt.typname
    END AS data_type
FROM pg_catalog.pg_opclass oc
INNER JOIN pg_catalog.pg_namespace ocn
  ON (oc.opcnamespace=ocn.oid)
INNER JOIN pg_catalog.pg_am am
  ON (oc.opcmethod=am.oid)
INNER JOIN pg_catalog.pg_type ocdat
  ON (oc.opckeytype=ocdat.oid)
INNER JOIN pg_catalog.pg_type datt
 ON (oc.opckeytype = datt.oid)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND ocn.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(operatorClassName)*/
  AND oc.opcname IN /*operatorClassName*/('%')
  /*end*/
ORDER BY ocn.nspname, oc.opcname
