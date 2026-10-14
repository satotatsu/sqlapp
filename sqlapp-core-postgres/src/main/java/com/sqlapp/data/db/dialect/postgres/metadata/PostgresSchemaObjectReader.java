/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/**
 * Bulk reader for PostgreSQL-only schema objects. One query per object kind,
 * never an extra JDBC SELECT per object. Version-specific catalog expressions
 * are selected before preparing SQL. Extension members are recreated by the
 * extension rather than exported twice.
 */
public class PostgresSchemaObjectReader {
	private final Dialect dialect;
	private record NativeIdentity(String schema, String catalog, String name, String parent) { }

	public PostgresSchemaObjectReader(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	private boolean since(Dialect version) { return dialect.compareTo(version) >= 0; }

	/** Marks native-model extension members so catalog recreation does not duplicate extension DDL. */
	public void markExtensionMembers(Connection connection, com.sqlapp.data.schemas.Schema schema) throws SQLException {
		if (!since(DialectHolder.postgreSQL91)) { return; }
		var members = new java.util.HashMap<String,String>();
		var namedMembers = new java.util.HashMap<NativeIdentity,String>();
		try (var statement=connection.prepareStatement("""
			SELECT d.classid::regclass::text class_name,d.objid::text object_id,e.extname,
			 n.nspname schema_name,COALESCE(c.relname,r.rulename,t.tgname) object_name,p.relname parent_name
			FROM pg_catalog.pg_depend d JOIN pg_catalog.pg_extension e ON e.oid=d.refobjid
			LEFT JOIN pg_catalog.pg_class c ON d.classid='pg_catalog.pg_class'::regclass AND c.oid=d.objid
			LEFT JOIN pg_catalog.pg_rewrite r ON d.classid='pg_catalog.pg_rewrite'::regclass AND r.oid=d.objid
			LEFT JOIN pg_catalog.pg_trigger t ON d.classid='pg_catalog.pg_trigger'::regclass AND t.oid=d.objid
			LEFT JOIN pg_catalog.pg_class p ON p.oid=COALESCE(r.ev_class,t.tgrelid)
			LEFT JOIN pg_catalog.pg_namespace n ON n.oid=COALESCE(c.relnamespace,p.relnamespace)
			WHERE d.refclassid='pg_catalog.pg_extension'::regclass AND d.deptype='e'
			"""); var rs=statement.executeQuery()) {
			while(rs.next()) {
				String className=rs.getString("class_name").replace("pg_catalog.","");
				members.put(className+":"+rs.getString("object_id"),rs.getString("extname"));
				if(rs.getString("object_name")!=null) {
					namedMembers.put(new NativeIdentity(rs.getString("schema_name"),className,
							rs.getString("object_name"),rs.getString("parent_name")),rs.getString("extname"));
				}
			}
		}
		for(var entry:schema.toMap().entrySet()) {
			if(!(entry.getValue() instanceof com.sqlapp.data.schemas.AbstractSchemaObjectCollection<?> collection)) continue;
			String catalog=switch(entry.getKey()) {
			case "functions","procedures" -> "pg_proc";
			case "types","domains" -> "pg_type";
			case "tables","views","mviews","sequences" -> "pg_class";
			case "operators" -> "pg_operator";
			case "operatorClasses" -> "pg_opclass";
			case "rules" -> "pg_rewrite";
			case "triggers" -> "pg_trigger";
			default -> "";
			};
			for(var object:collection) {
				String extension=members.get(catalog+":"+object.getId());
				if(extension==null) {
					String parent=null;
					if((catalog.equals("pg_rewrite") || catalog.equals("pg_trigger"))
							&& object instanceof com.sqlapp.data.schemas.properties.TableNameProperty<?> tableObject) {
						parent=tableObject.getTableName();
					}
					extension=namedMembers.get(new NativeIdentity(schema.getName(),catalog,object.getName(),parent));
				}
				object.getSpecifics().remove("postgres.extension");
				if(extension!=null) object.getSpecifics().put("postgres.extension",extension);
			}
		}
	}

	public List<PostgresSchemaObject> readSchema(Connection connection, String schema) throws SQLException {
		Objects.requireNonNull(schema, "schema");
		if (schema.isEmpty() || schema.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("schema must not be empty or contain NUL.");
		}
		var objects = new ArrayList<PostgresSchemaObject>();
		if (since(DialectHolder.postgreSQL91)) {
			read(connection, Kind.COLLATION, collationSql(), schema, objects);
			read(connection, Kind.FOREIGN_TABLE, foreignTableSql(), schema, objects);
			addForeignColumnDetails(connection, schema, objects);
		}
		if (since(DialectHolder.postgreSQL95)) {
			read(connection, Kind.POLICY, policySql(), schema, objects);
			read(connection, Kind.ROW_SECURITY, """
				SELECT n.nspname schema_name, c.relname AS name, NULL::text parent_name,
				 format('ALTER TABLE %I.%I %s ROW LEVEL SECURITY', n.nspname,c.relname,
				 CASE WHEN c.relrowsecurity THEN 'ENABLE' ELSE 'DISABLE' END) || '; ' ||
				 format('ALTER TABLE %I.%I %s FORCE ROW LEVEL SECURITY', n.nspname,c.relname,
				 CASE WHEN c.relforcerowsecurity THEN '' ELSE 'NO' END) ddl,
				 format('ALTER TABLE %I.%I DISABLE ROW LEVEL SECURITY',n.nspname,c.relname) drop_sql,
				 NULL::text AS owner, NULL::text remarks
				 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
				 WHERE n.nspname=? AND (c.relrowsecurity OR c.relforcerowsecurity)
				 ORDER BY c.relname
				""", schema, objects);
		}
		if (since(DialectHolder.postgreSQL100)) {
			read(connection, Kind.STATISTICS, statisticsSql(), schema, objects);
		}
		addPrivileges(connection, schema, objects);
		return List.copyOf(objects);
	}

	private void addForeignColumnDetails(Connection connection, String schema, List<PostgresSchemaObject> objects)
			throws SQLException {
		String partitionDetails=since(DialectHolder.postgreSQL100) ? """
			CASE WHEN c.relispartition THEN format('ALTER FOREIGN TABLE %I.%I ALTER COLUMN %I ',n.nspname,c.relname,a.attname) ||
			 CASE WHEN ad.adbin IS NULL THEN 'DROP DEFAULT' ELSE 'SET DEFAULT ' || pg_catalog.pg_get_expr(ad.adbin,ad.adrelid) END
			 ELSE NULL END default_sql,
			CASE WHEN c.relispartition AND a.attfdwoptions IS NOT NULL THEN
			 format('ALTER FOREIGN TABLE %I.%I ALTER COLUMN %I OPTIONS (',n.nspname,c.relname,a.attname) ||
			 (SELECT string_agg(format('ADD %I %L',option_name,option_value),', ' ORDER BY option_name)
			 FROM pg_catalog.pg_options_to_table(a.attfdwoptions)) || ')' ELSE NULL END options_sql,
			""" : "NULL::text default_sql,NULL::text options_sql,";
		if(since(DialectHolder.postgreSQL120)) {
			partitionDetails=partitionDetails.replace("c.relispartition THEN", "c.relispartition AND a.attgenerated='' THEN");
		}
		partitionDetails=partitionDetails.replace("format('ADD %I %L',option_name,option_value)",
				"format('ADD %I %L',option_name,CASE WHEN lower(option_name) IN ('password','sslpassword','secret','token')"
				+ " THEN '__SQLAPP_OPTION_' || lower(option_name) || '__' ELSE option_value END)");
		String sql="""
			SELECT c.relname,a.attname,%s
			 CASE WHEN d.description IS NULL THEN NULL ELSE format('COMMENT ON COLUMN %%I.%%I.%%I IS %%L',n.nspname,c.relname,a.attname,d.description) END comment_sql
			 FROM pg_catalog.pg_foreign_table ft JOIN pg_catalog.pg_class c ON c.oid=ft.ftrelid
			 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
			 JOIN pg_catalog.pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
			 LEFT JOIN pg_catalog.pg_attrdef ad ON ad.adrelid=c.oid AND ad.adnum=a.attnum
			 LEFT JOIN pg_catalog.pg_description d ON d.classoid='pg_catalog.pg_class'::regclass AND d.objoid=c.oid AND d.objsubid=a.attnum
			 WHERE n.nspname=? ORDER BY c.relname,a.attnum
			""".formatted(partitionDetails);
		var additions=new java.util.HashMap<String,List<String>>();
		try(var statement=connection.prepareStatement(sql)) {
			statement.setString(1,schema);
			try(var rs=statement.executeQuery()) {
				while(rs.next()) {
					for(String field:List.of("default_sql","options_sql","comment_sql")) {
						String value=rs.getString(field);
						if(value!=null) additions.computeIfAbsent(rs.getString("relname"),ignored->new ArrayList<>()).add(value);
					}
				}
			}
		}
		for(int i=0;i<objects.size();i++) {
			var object=objects.get(i);
			if(object.kind()!=Kind.FOREIGN_TABLE || !additions.containsKey(object.name())) continue;
			var definition=new ArrayList<>(object.definition());
			definition.addAll(additions.get(object.name()));
			objects.set(i,new PostgresSchemaObject(object.kind(),object.schemaName(),object.name(),object.parentName(),definition,
					object.dropDefinition(),object.attributes()));
		}
	}

	public List<PostgresSchemaObject> readCatalog(Connection connection) throws SQLException {
		var objects = new ArrayList<PostgresSchemaObject>();
		if (since(DialectHolder.postgreSQL91)) {
			read(connection, Kind.EXTENSION, """
				SELECT n.nspname schema_name,e.extname AS name,NULL::text parent_name,
				 format('CREATE EXTENSION %I WITH SCHEMA %I VERSION %L',e.extname,n.nspname,e.extversion) ddl,
				 format('DROP EXTENSION %I',e.extname) drop_sql,
				 pg_catalog.pg_get_userbyid(e.extowner) AS owner,pg_catalog.obj_description(e.oid,'pg_extension') remarks
				 FROM pg_catalog.pg_extension e JOIN pg_catalog.pg_namespace n ON n.oid=e.extnamespace
				 WHERE e.extname <> 'plpgsql' ORDER BY e.extname
				""", null, objects);
		}
		if (since(DialectHolder.postgreSQL91)) {
			read(connection, Kind.FOREIGN_DATA_WRAPPER, fdwSql(), null, objects);
			read(connection, Kind.SERVER, """
				SELECT NULL::text schema_name,s.srvname AS name,NULL::text parent_name,
				 format('CREATE SERVER %%I',s.srvname) ||
				 CASE WHEN s.srvtype IS NULL THEN '' ELSE format(' TYPE %%L',s.srvtype) END ||
				 CASE WHEN s.srvversion IS NULL THEN '' ELSE format(' VERSION %%L',s.srvversion) END ||
				 format(' FOREIGN DATA WRAPPER %%I',w.fdwname) %s ddl,
				 format('DROP SERVER %%I',s.srvname) drop_sql,
				 pg_catalog.pg_get_userbyid(s.srvowner) AS owner,pg_catalog.obj_description(s.oid,'pg_foreign_server') remarks
				 FROM pg_catalog.pg_foreign_server s JOIN pg_catalog.pg_foreign_data_wrapper w ON w.oid=s.srvfdw
				 WHERE %s ORDER BY s.srvname
				""".formatted(options("s.srvoptions"), notExtension("s", "pg_foreign_server")), null, objects);
			read(connection, Kind.USER_MAPPING, userMappingSql(), null, objects);
		}
		if (since(DialectHolder.postgreSQL100)) {
			read(connection, Kind.PUBLICATION, publicationSql(), null, objects);
			read(connection, Kind.SUBSCRIPTION, subscriptionSql(), null, objects);
		}
		addPrivileges(connection, null, objects);
		return List.copyOf(objects);
	}

	private void addPrivileges(Connection connection, String schema, List<PostgresSchemaObject> objects) throws SQLException {
		if(!since(DialectHolder.postgreSQL96)) return;
		String sources=schema==null ? """
			SELECT 'FOREIGN_DATA_WRAPPER' kind,w.fdwname AS name,NULL::text column_name,
			 format('FOREIGN DATA WRAPPER %I',w.fdwname) target,w.fdwacl acl,w.fdwowner AS owner
			 FROM pg_catalog.pg_foreign_data_wrapper w
			 UNION ALL
			 SELECT 'SERVER',s.srvname,NULL::text,format('FOREIGN SERVER %I',s.srvname),s.srvacl,s.srvowner
			 FROM pg_catalog.pg_foreign_server s
			""" : """
			SELECT 'FOREIGN_TABLE' kind,c.relname AS name,NULL::text column_name,
			 format('TABLE %I.%I',n.nspname,c.relname) target,c.relacl acl,c.relowner AS owner
			 FROM pg_catalog.pg_foreign_table ft JOIN pg_catalog.pg_class c ON c.oid=ft.ftrelid
			 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=?
			 UNION ALL
			 SELECT 'FOREIGN_TABLE',c.relname,a.attname,format('TABLE %I.%I',n.nspname,c.relname),a.attacl,c.relowner
			 FROM pg_catalog.pg_foreign_table ft JOIN pg_catalog.pg_class c ON c.oid=ft.ftrelid
			 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace JOIN pg_catalog.pg_attribute a ON a.attrelid=c.oid
			 WHERE n.nspname=? AND a.attnum>0 AND NOT a.attisdropped
			""";
		String sql="""
			SELECT x.kind,x.name,x.column_name,
			 format('GRANT %%s%%s ON %%s TO %%s%%s',a.privilege_type,
			 CASE WHEN x.column_name IS NULL THEN '' ELSE format(' (%%I)',x.column_name) END,x.target,
			 CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE quote_ident(pg_catalog.pg_get_userbyid(a.grantee)) END,
			 CASE WHEN a.is_grantable THEN ' WITH GRANT OPTION' ELSE '' END) grant_sql,
			 format('REVOKE ALL%%s ON %%s FROM PUBLIC',CASE WHEN x.column_name IS NULL THEN '' ELSE format(' (%%I)',x.column_name) END,x.target) reset_sql
			 FROM (%s) x CROSS JOIN LATERAL pg_catalog.aclexplode(x.acl) a
			 WHERE a.grantee<>x.owner ORDER BY x.kind,x.name,x.column_name NULLS FIRST,a.grantee,a.privilege_type
			""".formatted(sources);
		var grants=new java.util.HashMap<String,List<String>>();
		var resets=new java.util.HashSet<String>();
		try(var statement=connection.prepareStatement(sql)) {
			if(schema!=null) { statement.setString(1,schema); statement.setString(2,schema); }
			try(var rs=statement.executeQuery()) {
				while(rs.next()) {
					String key=rs.getString("kind")+":"+rs.getString("name");
					var definitions=grants.computeIfAbsent(key,ignored->new ArrayList<>());
					String reset=rs.getString("reset_sql");
					if(resets.add(reset)) definitions.add(reset);
					definitions.add(rs.getString("grant_sql"));
				}
			}
		}
		for(int i=0;i<objects.size();i++) {
			var object=objects.get(i);
			var additions=grants.get(object.kind()+":"+object.name());
			if(additions==null) continue;
			var definition=new ArrayList<>(object.definition());
			definition.addAll(additions);
			objects.set(i,new PostgresSchemaObject(object.kind(),object.schemaName(),object.name(),object.parentName(),
					definition,object.dropDefinition(),object.attributes()));
		}
	}

	private void read(Connection connection, Kind kind, String sql, String schema, List<PostgresSchemaObject> result)
			throws SQLException {
		try (var statement = connection.prepareStatement(sql)) {
			if (schema != null) { statement.setString(1, schema); }
			try (var rs = statement.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString("name");
					String schemaName = rs.getString("schema_name");
					String parent = rs.getString("parent_name");
					String ddl = rs.getString("ddl");
					if (ddl == null) {
						throw new SQLException("Cannot read complete " + kind + " definition for " + name
								+ "; check catalog privileges and supported object options.");
					}
					var definitions = new ArrayList<String>();
					definitions.add(ddl);
					var attributes = new LinkedHashMap<String, String>();
					String owner = rs.getString("owner");
					String remarks = rs.getString("remarks");
					String target = target(kind, schemaName, name, parent);
					if (owner != null) {
						attributes.put("owner", owner);
						if (kind != Kind.EXTENSION) { definitions.add("ALTER " + target + " OWNER TO " + quote(owner)); }
					}
					if (remarks != null) {
						attributes.put("remarks", remarks);
						definitions.add("COMMENT ON " + target + " IS " + literal(remarks));
					}
					if (kind == Kind.SUBSCRIPTION) {
						attributes.put("enabled", rs.getString("source_enabled"));
						attributes.put("connectionRequired", "true");
					}
					result.add(new PostgresSchemaObject(kind, schemaName, name, parent, definitions,
							rs.getString("drop_sql"), attributes));
				}
			}
		}
	}

	private String target(Kind kind, String schema, String name, String parent) {
		String qualified = schema == null ? quote(name) : quote(schema) + "." + quote(name);
		return switch (kind) {
		case FOREIGN_DATA_WRAPPER -> "FOREIGN DATA WRAPPER " + quote(name);
		case FOREIGN_TABLE -> "FOREIGN TABLE " + qualified;
		case POLICY -> "POLICY " + quote(name) + " ON " + quote(schema) + "." + quote(parent);
		case USER_MAPPING -> "USER MAPPING FOR " + (name.equals("PUBLIC") ? "PUBLIC" : quote(name)) + " SERVER " + quote(parent);
		case EXTENSION, SERVER, PUBLICATION, SUBSCRIPTION -> kind.name() + " " + quote(name);
		default -> kind.name() + " " + qualified;
		};
	}

	private String quote(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
	private String literal(String value) { return "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'"; }
	private String notExtension(String alias, String catalog) {
		if (!since(DialectHolder.postgreSQL91)) { return "TRUE"; }
		return "NOT EXISTS (SELECT 1 FROM pg_catalog.pg_depend d WHERE d.classid='pg_catalog." + catalog
				+ "'::regclass AND d.objid=" + alias + ".oid AND d.deptype='e')";
	}
	private String options(String expression) {
		return " || COALESCE((SELECT ' OPTIONS (' || string_agg(format('%I %L',option_name,"
				+ "CASE WHEN lower(option_name) IN ('password','sslpassword','secret','token') THEN '__SQLAPP_OPTION_' || lower(option_name) || '__' ELSE option_value END),', ' ORDER BY option_name)"
				+ " || ')' FROM pg_catalog.pg_options_to_table(" + expression + ")), '')";
	}

	String collationSql() {
		String locale = since(DialectHolder.postgreSQL170) ? "c.colllocale"
				: since(DialectHolder.postgreSQL150) ? "c.colliculocale" : "c.collcollate";
		String attributes = "format('LC_COLLATE = %L, LC_CTYPE = %L', c.collcollate, c.collctype)";
		if (since(DialectHolder.postgreSQL100)) {
			attributes = "CASE c.collprovider WHEN 'c' THEN 'provider = libc, ' || " + attributes
					+ " WHEN 'i' THEN format('provider = icu, locale = %L'," + locale + ")"
					+ (since(DialectHolder.postgreSQL170) ? " WHEN 'b' THEN format('provider = builtin, locale = %L',c.colllocale)" : "")
					+ " ELSE NULL END";
			attributes += " || CASE WHEN c.collversion IS NULL THEN '' ELSE format(', version = %L',c.collversion) END";
		}
		if (since(DialectHolder.postgreSQL120)) {
			attributes += " || ', deterministic = ' || c.collisdeterministic::text";
		}
		if (since(DialectHolder.postgreSQL160)) {
			attributes += " || CASE WHEN c.collicurules IS NULL THEN '' ELSE format(', rules = %L',c.collicurules) END";
		}
		return """
			SELECT n.nspname schema_name,c.collname AS name,NULL::text parent_name,
			 format('CREATE COLLATION %%I.%%I (',n.nspname,c.collname) || %s || ')' ddl,
			 format('DROP COLLATION %%I.%%I',n.nspname,c.collname) drop_sql,
			 pg_catalog.pg_get_userbyid(c.collowner) AS owner,pg_catalog.obj_description(c.oid,'pg_collation') remarks
			 FROM pg_catalog.pg_collation c JOIN pg_catalog.pg_namespace n ON n.oid=c.collnamespace
			 WHERE n.nspname=? AND c.collencoding IN (-1,pg_catalog.pg_char_to_encoding(current_setting('server_encoding')))
			 AND n.nspname NOT IN ('pg_catalog','information_schema')
			 AND %s ORDER BY c.collname
			""".formatted(attributes, notExtension("c", "pg_collation"));
	}

	String fdwSql() {
		String handler = since(DialectHolder.postgreSQL91) ? """
			 CASE WHEN w.fdwhandler=0 THEN ' NO HANDLER' ELSE ' HANDLER ' ||
			 (SELECT format('%I.%I',n.nspname,p.proname) FROM pg_catalog.pg_proc p
			 JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE p.oid=w.fdwhandler) END ||
			""" : "";
		return """
			SELECT NULL::text schema_name,w.fdwname AS name,NULL::text parent_name,
			 format('CREATE FOREIGN DATA WRAPPER %I',w.fdwname) || %s
			 CASE WHEN w.fdwvalidator=0 THEN ' NO VALIDATOR' ELSE ' VALIDATOR ' ||
			 (SELECT format('%%I.%%I',n.nspname,p.proname) FROM pg_catalog.pg_proc p
			 JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE p.oid=w.fdwvalidator) END %s ddl,
			 format('DROP FOREIGN DATA WRAPPER %%I',w.fdwname) drop_sql,
			 pg_catalog.pg_get_userbyid(w.fdwowner) AS owner,pg_catalog.obj_description(w.oid,'pg_foreign_data_wrapper') remarks
			 FROM pg_catalog.pg_foreign_data_wrapper w WHERE %s ORDER BY w.fdwname
			""".replace("format('CREATE FOREIGN DATA WRAPPER %I'", "format('CREATE FOREIGN DATA WRAPPER %%I'")
			.formatted(handler, options("w.fdwoptions"), notExtension("w", "pg_foreign_data_wrapper"));
	}

	String userMappingSql() {
		// Do not read pg_user_mapping.umoptions directly: it can expose passwords.
		// An unreadable mapping is an error, not an empty option list.
		return """
			SELECT NULL::text schema_name,CASE WHEN m.umuser=0 THEN 'PUBLIC' ELSE pg_catalog.pg_get_userbyid(m.umuser) END AS name,
			 s.srvname parent_name,
			 CASE WHEN m.umoptions IS NULL AND NOT (current_user=pg_catalog.pg_get_userbyid(s.srvowner)
			 OR (SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname=current_user)) THEN NULL ELSE
			 format('CREATE USER MAPPING FOR %s SERVER %I',CASE WHEN m.umuser=0 THEN 'PUBLIC'
			 ELSE quote_ident(pg_catalog.pg_get_userbyid(m.umuser)) END,s.srvname) %s END ddl,
			 format('DROP USER MAPPING FOR %%s SERVER %%I',CASE WHEN m.umuser=0 THEN 'PUBLIC'
			 ELSE quote_ident(pg_catalog.pg_get_userbyid(m.umuser)) END,s.srvname) drop_sql,
			 NULL::text AS owner,NULL::text remarks
			 FROM pg_catalog.pg_user_mappings m JOIN pg_catalog.pg_foreign_server s ON s.oid=m.srvid
			 WHERE %s ORDER BY s.srvname,m.umuser
			""".replace("format('CREATE USER MAPPING FOR %s SERVER %I'", "format('CREATE USER MAPPING FOR %%s SERVER %%I'")
			.formatted(options("m.umoptions"), notExtension("s", "pg_foreign_server"));
	}

	String foreignTableSql() {
		String sql = """
			SELECT n.nspname schema_name,c.relname AS name,NULL::text parent_name,
			 format('CREATE FOREIGN TABLE %I.%I (',n.nspname,c.relname) ||
			 COALESCE((SELECT string_agg(format('%%I %%s',a.attname,pg_catalog.format_type(a.atttypid,a.atttypmod)) ||
			 CASE WHEN a.attcollation=0 THEN '' ELSE (SELECT format(' COLLATE %%I.%%I',cn.nspname,co.collname)
			 FROM pg_catalog.pg_collation co JOIN pg_catalog.pg_namespace cn ON cn.oid=co.collnamespace WHERE co.oid=a.attcollation) END
			 %s || CASE WHEN ad.adbin IS NULL THEN '' ELSE ' DEFAULT ' || pg_catalog.pg_get_expr(ad.adbin,ad.adrelid) END ||
			 CASE WHEN a.attnotnull THEN ' NOT NULL' ELSE '' END, ', ' ORDER BY a.attnum)
			 FROM pg_catalog.pg_attribute a LEFT JOIN pg_catalog.pg_attrdef ad ON ad.adrelid=a.attrelid AND ad.adnum=a.attnum
			 WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),'') ||
			 COALESCE((SELECT ', ' || string_agg(format('CONSTRAINT %%I %%s',con.conname,pg_catalog.pg_get_constraintdef(con.oid)),', ' ORDER BY con.conname)
			 FROM pg_catalog.pg_constraint con WHERE con.conrelid=c.oid AND con.contype='c'),'') || ')'
			 || format(' SERVER %%I',s.srvname) %s ddl,
			 format('DROP FOREIGN TABLE %%I.%%I',n.nspname,c.relname) drop_sql,
			 pg_catalog.pg_get_userbyid(c.relowner) AS owner,pg_catalog.obj_description(c.oid,'pg_class') remarks
			 FROM pg_catalog.pg_foreign_table ft JOIN pg_catalog.pg_class c ON c.oid=ft.ftrelid
			 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace JOIN pg_catalog.pg_foreign_server s ON s.oid=ft.ftserver
			 WHERE n.nspname=? AND %s ORDER BY c.relname
			""".replace("format('CREATE FOREIGN TABLE %I.%I ('", "format('CREATE FOREIGN TABLE %%I.%%I ('")
			.formatted(since(DialectHolder.postgreSQL92) ? options("a.attfdwoptions") : "", options("ft.ftoptions"), notExtension("c", "pg_class"));
		String inherits = " || COALESCE((SELECT ' INHERITS (' || string_agg(format('%I.%I',pn.nspname,pc.relname),', ' ORDER BY i.inhseqno) || ')'"
				+ " FROM pg_catalog.pg_inherits i JOIN pg_catalog.pg_class pc ON pc.oid=i.inhparent"
				+ " JOIN pg_catalog.pg_namespace pn ON pn.oid=pc.relnamespace WHERE i.inhrelid=c.oid),'')";
		if (since(DialectHolder.postgreSQL100)) {
			String partition = "CASE WHEN c.relispartition THEN format('CREATE FOREIGN TABLE %I.%I PARTITION OF ',n.nspname,c.relname) ||"
					+ " (SELECT format('%I.%I',pn.nspname,pc.relname) FROM pg_catalog.pg_inherits i"
					+ " JOIN pg_catalog.pg_class pc ON pc.oid=i.inhparent JOIN pg_catalog.pg_namespace pn ON pn.oid=pc.relnamespace"
					+ " WHERE i.inhrelid=c.oid) || ' ' || pg_catalog.pg_get_expr(c.relpartbound,c.oid) ELSE ";
			sql=sql.replace("format('CREATE FOREIGN TABLE %I.%I ('",partition+"format('CREATE FOREIGN TABLE %I.%I ('");
			inherits += " END";
		}
		if(since(DialectHolder.postgreSQL120)) {
			sql=sql.replace("CASE WHEN ad.adbin IS NULL THEN '' ELSE ' DEFAULT ' || pg_catalog.pg_get_expr(ad.adbin,ad.adrelid) END",
					"CASE WHEN ad.adbin IS NULL THEN '' WHEN a.attgenerated<>'' THEN ' GENERATED ALWAYS AS (' || pg_catalog.pg_get_expr(ad.adbin,ad.adrelid) || ') ' ||"
					+ " CASE WHEN a.attgenerated='s' THEN 'STORED' ELSE 'VIRTUAL' END ELSE ' DEFAULT ' || pg_catalog.pg_get_expr(ad.adbin,ad.adrelid) END");
		}
		sql=sql.replace("|| format(' SERVER %I',s.srvname)",inherits+" || format(' SERVER %I',s.srvname)");
		// An inherited foreign table may itself be a parent; preserve parent-before-child order.
		sql=sql.replace("ORDER BY c.relname", "ORDER BY (WITH RECURSIVE ancestors(oid) AS (SELECT inhparent FROM pg_catalog.pg_inherits WHERE inhrelid=c.oid"
				+ " UNION ALL SELECT i.inhparent FROM pg_catalog.pg_inherits i JOIN ancestors a ON i.inhrelid=a.oid) SELECT count(*) FROM ancestors),c.relname");
		return sql;
	}

	String policySql() {
		return """
			SELECT n.nspname schema_name,p.polname AS name,c.relname parent_name,
			 format('CREATE POLICY %I ON %I.%I',p.polname,n.nspname,c.relname) %s ||
			 ' FOR ' || CASE p.polcmd WHEN '*' THEN 'ALL' WHEN 'r' THEN 'SELECT' WHEN 'a' THEN 'INSERT'
			 WHEN 'w' THEN 'UPDATE' WHEN 'd' THEN 'DELETE' END || ' TO ' ||
			 (SELECT string_agg(CASE WHEN role=0 THEN 'PUBLIC' ELSE quote_ident(pg_catalog.pg_get_userbyid(role)) END,', ' ORDER BY role)
			 FROM unnest(p.polroles) role) ||
			 CASE WHEN p.polqual IS NULL THEN '' ELSE ' USING (' || pg_catalog.pg_get_expr(p.polqual,p.polrelid) || ')' END ||
			 CASE WHEN p.polwithcheck IS NULL THEN '' ELSE ' WITH CHECK (' || pg_catalog.pg_get_expr(p.polwithcheck,p.polrelid) || ')' END ddl,
			 format('DROP POLICY %%I ON %%I.%%I',p.polname,n.nspname,c.relname) drop_sql,
			 NULL::text AS owner,pg_catalog.obj_description(p.oid,'pg_policy') remarks
			 FROM pg_catalog.pg_policy p JOIN pg_catalog.pg_class c ON c.oid=p.polrelid
			 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? ORDER BY c.relname,p.polname
			""".replace("format('CREATE POLICY %I ON %I.%I'", "format('CREATE POLICY %%I ON %%I.%%I'")
			.formatted(since(DialectHolder.postgreSQL100) ? " || CASE WHEN p.polpermissive THEN ' AS PERMISSIVE' ELSE ' AS RESTRICTIVE' END" : "");
	}

	String statisticsSql() {
		return """
			SELECT n.nspname schema_name,s.stxname AS name,NULL::text parent_name,
			 pg_catalog.pg_get_statisticsobjdef(s.oid) %s ddl,
			 format('DROP STATISTICS %%I.%%I',n.nspname,s.stxname) drop_sql,
			 pg_catalog.pg_get_userbyid(s.stxowner) AS owner,pg_catalog.obj_description(s.oid,'pg_statistic_ext') remarks
			 FROM pg_catalog.pg_statistic_ext s JOIN pg_catalog.pg_namespace n ON n.oid=s.stxnamespace
			 WHERE n.nspname=? AND %s ORDER BY s.stxname
			""".formatted(since(DialectHolder.postgreSQL130) ? " || CASE WHEN s.stxstattarget<0 THEN '' ELSE format('; ALTER STATISTICS %I.%I SET STATISTICS %s',n.nspname,s.stxname,s.stxstattarget) END" : "",
				notExtension("s", "pg_statistic_ext"));
	}

	String publicationSql() {
		String tables = "(SELECT string_agg(format('ONLY %I.%I',n.nspname,c.relname)";
		if (since(DialectHolder.postgreSQL150)) {
			tables += " || CASE WHEN pr.prattrs IS NULL THEN '' ELSE ' (' || (SELECT string_agg(quote_ident(a.attname),', ' ORDER BY a.attnum)"
					+ " FROM pg_catalog.pg_attribute a WHERE a.attrelid=c.oid AND a.attnum=ANY(pr.prattrs)) || ')' END"
					+ " || CASE WHEN pr.prqual IS NULL THEN '' ELSE ' WHERE (' || pg_catalog.pg_get_expr(pr.prqual,pr.prrelid) || ')' END";
		}
		tables += ", ', ' ORDER BY n.nspname,c.relname) FROM pg_catalog.pg_publication_rel pr JOIN pg_catalog.pg_class c ON c.oid=pr.prrelid"
				+ " JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE pr.prpubid=p.oid)";
		String schemas = since(DialectHolder.postgreSQL150)
				? " || COALESCE((SELECT CASE WHEN " + tables + " IS NULL THEN ' FOR ' ELSE ', ' END || 'TABLES IN SCHEMA ' || string_agg(quote_ident(n.nspname),', ' ORDER BY n.nspname)"
				+ " FROM pg_catalog.pg_publication_namespace pn JOIN pg_catalog.pg_namespace n ON n.oid=pn.pnnspid WHERE pn.pnpubid=p.oid),'')" : "";
		return """
			SELECT NULL::text schema_name,p.pubname AS name,NULL::text parent_name,
			 format('CREATE PUBLICATION %%I',p.pubname) || CASE WHEN p.puballtables THEN ' FOR ALL TABLES' ELSE
			 COALESCE(' FOR TABLE ' || %s,'') %s END ||
			 ' WITH (publish = ' || quote_literal(concat_ws(', ',CASE WHEN p.pubinsert THEN 'insert' END,
			 CASE WHEN p.pubupdate THEN 'update' END,CASE WHEN p.pubdelete THEN 'delete' END %s)) %s %s || ')' ddl,
			 format('DROP PUBLICATION %%I',p.pubname) drop_sql,
			 pg_catalog.pg_get_userbyid(p.pubowner) AS owner,pg_catalog.obj_description(p.oid,'pg_publication') remarks
			 FROM pg_catalog.pg_publication p ORDER BY p.pubname
			""".formatted(tables, schemas, since(DialectHolder.postgreSQL110) ? ", CASE WHEN p.pubtruncate THEN 'truncate' END" : "",
				since(DialectHolder.postgreSQL130) ? " || ', publish_via_partition_root = ' || p.pubviaroot::text" : "",
				since(DialectHolder.postgreSQL180) ? " || ', publish_generated_columns = ' || quote_literal(CASE p.pubgencols WHEN 's' THEN 'stored' ELSE 'none' END)" : "");
	}

	String subscriptionSql() {
		String extra = "";
		if (since(DialectHolder.postgreSQL140)) {
			extra += " || ', binary = ' || s.subbinary::text || ', streaming = ' || quote_literal("
					+ (since(DialectHolder.postgreSQL160) ? "CASE s.substream WHEN 'p' THEN 'parallel' WHEN 't' THEN 'on' ELSE 'off' END"
							: "CASE WHEN s.substream THEN 'on' ELSE 'off' END") + ")";
		}
		if (since(DialectHolder.postgreSQL150)) { extra += " || ', two_phase = ' || (s.subtwophasestate<>'d')::text || ', disable_on_error = ' || s.subdisableonerr::text"; }
		if (since(DialectHolder.postgreSQL160)) { extra += " || ', password_required = ' || s.subpasswordrequired::text || ', run_as_owner = ' || s.subrunasowner::text || ', origin = ' || quote_literal(s.suborigin)"; }
		if (since(DialectHolder.postgreSQL170)) { extra += " || ', failover = ' || s.subfailover::text"; }
		return """
			SELECT NULL::text schema_name,s.subname AS name,NULL::text parent_name,
			 format('CREATE SUBSCRIPTION %%I CONNECTION %s PUBLICATION ',s.subname) ||
			 (SELECT string_agg(quote_ident(p),', ' ORDER BY p) FROM unnest(s.subpublications) p) ||
			 ' WITH (connect = false, enabled = false, create_slot = false, copy_data = false, slot_name = ' ||
			 CASE WHEN s.subslotname IS NULL THEN 'NONE' ELSE quote_literal(s.subslotname) END ||
			 ', synchronous_commit = ' || quote_literal(s.subsynccommit) %s || ')' ddl,
			 format('ALTER SUBSCRIPTION %%I DISABLE; ALTER SUBSCRIPTION %%I SET (slot_name=NONE); DROP SUBSCRIPTION %%I',s.subname,s.subname,s.subname) drop_sql,
			 pg_catalog.pg_get_userbyid(s.subowner) AS owner,pg_catalog.shobj_description(s.oid,'pg_subscription') remarks,
			 s.subenabled source_enabled
			 FROM pg_catalog.pg_subscription s WHERE s.subdbid=(SELECT oid FROM pg_catalog.pg_database WHERE datname=current_database())
			 ORDER BY s.subname
			""".formatted(PostgresSchemaObjects.CONNECTION_TOKEN, extra);
	}
}
