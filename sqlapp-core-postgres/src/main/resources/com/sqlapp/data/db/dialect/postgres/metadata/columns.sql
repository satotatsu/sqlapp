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
	,case
	 when (c.relkind::varchar IN ('r', 'p') and n.nspname not in ('information_schema', 'pgagent')) 
	      then pg_get_serial_sequence(concat('"', c.relname, '"'), a.attname)
	      else null
	 end  AS sequence_name
	,case when a.atttypid IN (1042, 1043) /*CHAR,VARCHAR*/
	      then a.atttypmod -4
	      when a.atttypid IN (1560, 1562) /*BIT,VARBIT*/
	      then a.atttypmod
	      else null
	 end AS max_length
	,case a.atttypid
	      when 1700 then
	           case when a.atttypmod = -1 then null
	                else (a.atttypmod>>16) &65535
	           end
	      else null
	 end AS numeric_precision
	,case a.atttypid
	      when 1700 then
	           case when a.atttypmod = -1 then null
	                else (a.atttypmod-4) &65535
	           end
	      else null
	 end AS numeric_scale
	,case when a.atttypid IN (1082) /*date*/
	      then 0
	      when a.atttypid IN (1083, 1114, 1184, 1266) /*time, timestamp, timetz, timestamptz*/
	      then case when a.atttypmod < 0 then 6 else a.atttypmod end
	      else null
	 end AS datetime_scale
	,case when a.atttypid IN (1186) /*interval*/
	      then case when ((a.atttypmod)&65535)=65535 then null else (a.atttypmod)&65535 end
	      else null
	 end AS interval_scale
	,case when a.atttypid IN (1186) then /*interval*/
	      case ((a.atttypmod)>>16)
		      when 32767 then 'interval'
		      when 4 then 'interval year'
		      when 2 then 'interval month'
		      when 8 then 'interval day'
		      when 6 then 'interval year to month'
		      when 8 then 'interval day'
		      when 1024 then 'interval hour'
		      when 1032 then 'interval day to hour'
		      when 2048 then 'interval minute'
		      when 3072 then 'interval hour to minute'
		      when 3080 then 'interval day to minute'
		      when 4096 then 'interval second'
		      when 6144 then 'interval minute to second'
		      when 7168 then 'interval hour to second'
		      when 7176 then 'interval day to second'
		      else 'interval'
		end
	     else null
	 end as interval_type_name
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
