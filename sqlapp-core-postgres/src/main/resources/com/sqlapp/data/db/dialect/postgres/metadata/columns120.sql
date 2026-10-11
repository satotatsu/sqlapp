/* PostgreSQL 12+ adds attgenerated; keep the remainder in the shared query. */
SELECT
	  current_database() AS catalog_name
	, n.nspname, c.relname, c.relkind, a.attname, t.typname, a.atttypid
	, a.attnotnull, a.atttypmod, a.attndims, a.attlen, a.attnum
	, col_description(c.oid, a.attnum) AS remarks
	, pg_catalog.pg_get_expr(def.adbin, def.adrelid) AS adsrc
	, dsc.description, t.typbasetype, t.typtype
	, seqcls.relname AS sequence_name
	, seq.seqstart AS identity_start, seq.seqincrement AS identity_increment
	, seq.seqmax AS identity_maximum, seq.seqmin AS identity_minimum
	, seq.seqcycle AS identity_cycle, a.attidentity, a.attgenerated
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END) IN (1042,1043) THEN CASE WHEN a.atttypmod < 0 THEN null ELSE a.atttypmod-4 END WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END) IN (1560,1562) THEN CASE WHEN a.atttypmod < 0 THEN null ELSE a.atttypmod END END AS max_length
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END)=1700 AND a.atttypmod<>-1 THEN ((a.atttypmod-4)>>16)&65535 END AS numeric_precision
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END)=1700 AND a.atttypmod<>-1 THEN (((a.atttypmod-4)&2047)#1024)-1024 END AS numeric_scale
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END)=1082 THEN 0 WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END) IN (1083,1114,1184,1266) THEN CASE WHEN a.atttypmod<0 THEN 6 ELSE a.atttypmod END END AS datetime_scale
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END)=1186 THEN CASE WHEN (a.atttypmod&65535)=65535 THEN null ELSE a.atttypmod&65535 END END AS interval_scale
	, CASE WHEN (CASE WHEN a.attndims > 0 THEN t.typelem ELSE t.oid END)=1186 THEN CASE (a.atttypmod >> 16) WHEN 4 THEN 'interval year' WHEN 2 THEN 'interval month' WHEN 8 THEN 'interval day' WHEN 6 THEN 'interval year to month' WHEN 1024 THEN 'interval hour' WHEN 1032 THEN 'interval day to hour' WHEN 2048 THEN 'interval minute' WHEN 3072 THEN 'interval hour to minute' WHEN 3080 THEN 'interval day to minute' WHEN 4096 THEN 'interval second' WHEN 6144 THEN 'interval minute to second' WHEN 7168 THEN 'interval hour to second' WHEN 7176 THEN 'interval day to second' ELSE 'interval' END END AS interval_type_name
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON c.relnamespace=n.oid
JOIN pg_catalog.pg_attribute a ON c.oid=a.attrelid
JOIN pg_catalog.pg_type t ON a.atttypid=t.oid
LEFT JOIN pg_catalog.pg_attrdef def ON a.attrelid=def.adrelid AND a.attnum=def.adnum
LEFT JOIN pg_catalog.pg_description dsc ON c.oid=dsc.objoid AND a.attnum=dsc.objsubid
LEFT JOIN pg_depend dep ON dep.refclassid=('pg_class'::regclass)::oid AND dep.refobjid=c.oid AND dep.refobjsubid=a.attnum
LEFT JOIN pg_sequence seq ON dep.classid=('pg_class'::regclass)::oid AND dep.objid=seq.seqrelid AND dep.deptype='i'::"char"
LEFT JOIN pg_class seqcls ON seq.seqrelid=seqcls.oid
WHERE a.attnum>0 AND NOT a.attisdropped
  /*if isNotEmpty(relkind) */ AND c.relkind::varchar IN /*relkind*/('r','v','f','m','p') /*end*/
  /*if isNotEmpty(schemaName) */ AND n.nspname IN /*schemaName*/('%') /*end*/
  /*if isNotEmpty(tableName) */ AND c.relname IN /*tableName*/('%') /*end*/
  /*if isNotEmpty(columnName) */ AND a.attname IN /*columnName*/('%') /*end*/
ORDER BY n.nspname,c.relname,a.attnum
