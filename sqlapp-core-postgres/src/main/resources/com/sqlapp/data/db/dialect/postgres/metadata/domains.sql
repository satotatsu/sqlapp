SELECT
	  current_database() AS domain_catalog
	, n.nspname AS domain_schema
	, t.typname AS domain_name
	, t.oid
	, CASE WHEN t.typelem <> 0 AND t.typlen = -1 THEN 'ARRAY'
	       WHEN nb.nspname = 'pg_catalog' THEN format_type(t.typbasetype, null)
	       ELSE 'USER-DEFINED' END
	 AS typname
	, t.typnotnull
	, t.typndims
	, t.typdefault
	, obj_description(t.oid, current_database()) AS remarks
	,CASE
	 WHEN t.typbasetype IN (1042, 1043) /*CHAR,VARCHAR*/
	 THEN
	   CASE t.typtypmod
	   WHEN -1
	   THEN CAST(2^30 AS integer)
	   ELSE
	     t.typtypmod -4
	   END
	 WHEN t.typbasetype IN (1560, 1562) /*BIT,VARBIT*/
	 THEN t.typtypmod
	 ELSE null
	 end AS max_length
	,CASE t.typbasetype
	      WHEN 1700 THEN
	           CASE WHEN t.typtypmod = -1 THEN null
	                ELSE (t.typtypmod>>16) &65535
	           END
	      ELSE null
	 end AS numeric_precision
	,CASE t.typbasetype
	      WHEN 1700 THEN
	           CASE WHEN t.typtypmod = -1 THEN null
	                ELSE (t.typtypmod-4) &65535
	           END
	      ELSE null
	 end AS numeric_scale
	,CASE WHEN t.typbasetype IN (1082) /*date*/
	      THEN 0
	      WHEN t.typbasetype IN (1083, 1114, 1184, 1266) /*time, timestamp, timetz, timestamptz*/
	      THEN CASE WHEN t.typtypmod < 0 THEN 6 ELSE t.typtypmod END
	      ELSE null
	 end AS datetime_scale
	,CASE WHEN t.typbasetype IN (1186) /*interval*/
	      then CASE WHEN ((t.typtypmod)&65535)=65535 THEN null ELSE (t.typtypmod)&65535 end
	      ELSE null
	 end AS interval_scale
	,CASE WHEN t.typbasetype IN (1186) THEN /*interval*/
	      CASE ((t.typtypmod)>>16)
		      WHEN 32767 THEN 'interval'
		      WHEN 4 THEN 'interval year'
		      WHEN 2 THEN 'interval month'
		      WHEN 8 THEN 'interval day'
		      WHEN 6 THEN 'interval year to month'
		      WHEN 8 THEN 'interval day'
		      WHEN 1024 THEN 'interval hour'
		      WHEN 1032 THEN 'interval day to hour'
		      WHEN 2048 THEN 'interval minute'
		      WHEN 3072 THEN 'interval hour to minute'
		      WHEN 3080 THEN 'interval day to minute'
		      WHEN 4096 THEN 'interval second'
		      WHEN 6144 THEN 'interval minute to second'
		      WHEN 7168 THEN 'interval hour to second'
		      WHEN 7176 THEN 'interval day to second'
		      ELSE 'interval'
		  END
	      ELSE null
	 END AS interval_type_name
	 , pg_get_constraintdef(con.oid) as consrc --ドメイン制約式
	 , condeferrable   AS is_deferrable
	 , condeferred   AS initially_deferred
FROM pg_catalog.pg_type t
INNER JOIN pg_catalog.pg_namespace n
 ON (t.typnamespace = n.oid)
INNER JOIN pg_catalog.pg_type bt
 ON (t.typbasetype = bt.oid)
INNER JOIN pg_catalog.pg_namespace nb
 ON (bt.typnamespace = nb.oid)
LEFT OUTER JOIN pg_catalog.pg_constraint con
 ON (n.oid = con.connamespace AND t.oid = con.contypid)
WHERE 1=1
  AND t.typtype = 'd'
  /*if isNotEmpty(schemaName)*/
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(domainName)*/
  AND t.typname IN /*domainName*/('%')
  /*end*/
ORDER BY n.nspname, t.typname