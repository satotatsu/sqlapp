SELECT
	  current_database() AS catalog_name
	, n.nspname
	,c.relname
	,c.relkind
	,a.attname
	,t.typname
	,a.atttypid
	,a.attnotnull
	,a.atttypmod
	,a.attndims
	,a.attlen
	,a.attnum
	,col_description(c.oid, a.attnum) AS remarks
	,pg_catalog.pg_get_expr(def.adbin, def.adrelid) AS adsrc
	,dsc.description
	,t.typbasetype
	,t.typtype
    ,seqcls.relname AS sequence_name
	,seq.seqstart AS identity_start
    ,seq.seqincrement AS identity_increment
    ,seq.seqmax AS identity_maximum
    ,seq.seqmin AS identity_minimum
    ,seq.seqcycle AS identity_cycle
	,CASE WHEN a.atttypid IN (1042, 1043) /*CHAR,VARCHAR*/
	      THEN a.atttypmod -4
	      WHEN a.atttypid IN (1560, 1562) /*BIT,VARBIT*/
	      THEN a.atttypmod
	      else null
	 END ASmax_length
	,CASE a.atttypid
	      WHEN 1700 then
	           case WHEN a.atttypmod = -1 THEN null
	                else (a.atttypmod>>16) &65535
	           end
	      else null
	 END ASnumeric_precision
	,CASE a.atttypid
	      WHEN 1700 then
	           case WHEN a.atttypmod = -1 THEN null
	                else (a.atttypmod-4) &65535
	           end
	      else null
	 END ASnumeric_scale
	,CASE WHEN a.atttypid IN (1082) /*date*/
	      THEN 0
	      WHEN a.atttypid IN (1083, 1114, 1184, 1266) /*time, timestamp, timetz, timestamptz*/
	      THEN case WHEN a.atttypmod < 0 THEN 6 else a.atttypmod end
	      else null
	 END ASdatetime_scale
	,CASE WHEN a.atttypid IN (1186) /*interval*/
	      THEN case WHEN ((a.atttypmod)&65535)=65535 THEN null else (a.atttypmod)&65535 end
	      else null
	 END ASinterval_scale
	,CASE
	 WHEN a.atttypid IN (1186) THEN /*interval*/
	     CASE ((a.atttypmod)>>16)
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
		      else 'interval'
		END
	 ELSE null
	 END AS interval_type_name
FROM pg_catalog.pg_class c
INNER JOIN pg_catalog.pg_namespace n
  ON (c.relnamespace = n.oid)
INNER JOIN pg_catalog.pg_attribute a
  ON (c.oid=a.attrelid)
INNER JOIN pg_catalog.pg_type t
  ON (a.atttypid = t.oid)
LEFT OUTER JOIN pg_catalog.pg_attrdef def
  ON (a.attrelid=def.adrelid AND a.attnum = def.adnum)
LEFT OUTER JOIN pg_catalog.pg_description dsc
  ON (c.oid=dsc.objoid AND a.attnum = dsc.objsubid)
LEFT OUTER JOIN pg_catalog.pg_class dc
  ON (dc.oid=dsc.classoid AND dc.relname='pg_class')
LEFT OUTER JOIN pg_catalog.pg_namespace dn
  ON (dc.relnamespace=dn.oid AND dn.nspname='pg_catalog')
LEFT OUTER JOIN pg_depend dep
  ON (
  dep.refclassid = ('pg_class'::regclass)::oid
  AND dep.refobjid = c.oid
  AND dep.refobjsubid = a.attnum
)  
LEFT OUTER JOIN pg_sequence seq 
  ON (
      dep.classid = ('pg_class'::regclass)::oid
      AND dep.objid = seq.seqrelid
      AND dep.deptype = 'i'::"char"
)
LEFT OUTER JOIN pg_class seqcls
  ON (
      seq.seqrelid = seqcls.oid
)
WHERE a.attnum > 0
  AND NOT a.attisdropped 
  /*if isNotEmpty(relkind) */
  AND c.relkind::varchar IN /*relkind*/('r','v','f','m','p')
  /*end*/
  /*if isNotEmpty(schemaName) */
  AND n.nspname IN /*schemaName*/('%')
  /*end*/
  /*if isNotEmpty(tableName) */
  AND c.relname IN /*tableName*/('%')
  /*end*/
  /*if isNotEmpty(columnName) */
  AND a.attname IN /*columnName*/('%')
  /*end*/
ORDER BY n.nspname, c.relname, a.attnum
