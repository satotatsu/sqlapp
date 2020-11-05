SELECT
    current_database() AS catalog_name
  , ocn.nspname AS schema_name
  , opc.opcname AS operator_class_name
  , amop.amopstrategy AS operator_family_strategy
  , amop.amopmethod
  , am.amname
  , opn.nspname AS operator_schema
  , op.oprname AS operator_name
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
FROM pg_catalog.pg_opclass opc
INNER JOIN pg_catalog.pg_namespace ocn
  ON (opc.opcnamespace=ocn.oid)
INNER JOIN pg_catalog.pg_opfamily opf
  ON (opc.opcfamily=opf.oid)
INNER JOIN pg_catalog.pg_amop amop
  ON (opf.oid=amop.amopfamily)
INNER JOIN pg_catalog.pg_am am
  ON (amop.amopmethod=am.oid)
INNER JOIN pg_catalog.pg_operator op
  ON (amop.amopopr = op.oid)
INNER JOIN pg_catalog.pg_namespace opn
  ON (op.oprnamespace=opn.oid)
INNER JOIN pg_catalog.pg_type lt
  ON (amop.amoplefttype=lt.oid)
INNER JOIN pg_catalog.pg_type rt
  ON (amop.amoprighttype=rt.oid)
WHERE 1=1
  /*if isNotEmpty(schemaName) */
  AND ocn.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(operatorClassName)*/
  AND opc.opcname IN /*operatorClassName*/('%')
  /*end*/
ORDER BY ocn.nspname, opc.opcname, amop.amopstrategy
