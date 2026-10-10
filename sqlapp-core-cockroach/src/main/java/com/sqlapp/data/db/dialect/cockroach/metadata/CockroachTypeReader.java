/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
public class CockroachTypeReader extends TypeReader {
	public CockroachTypeReader(Dialect dialect) { super(dialect); }
	@Override protected List<Type> doGetAll(Connection c,ParametersContext context,ProductVersionInfo version) {
		var result=new ArrayList<Type>();
		boolean multiRegion;
		try(var sql=c.createStatement();var rows=sql.executeQuery("SHOW REGIONS FROM DATABASE "+CockroachMetadata.quote(c.getCatalog()))) {multiRegion=rows.next();}
		catch(SQLException e) {throw new IllegalStateException("Cannot read CockroachDB database regions",e);}
		try(var sql=c.createStatement(); var rows=sql.executeQuery("SELECT n.nspname,t.typname,t.oid FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace WHERE t.typtype='e' ORDER BY n.nspname,t.typname")) {
			while(rows.next()) {
				String schema=rows.getString(1),name=rows.getString(2);
				// PRIMARY REGION creates this engine-owned type; replaying CREATE TYPE duplicates it.
				if(multiRegion && "public".equals(schema) && "crdb_internal_region".equals(name)) continue;
				if(!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context,"schemaName",schema) || !CockroachMetadata.matches(context,"typeName",name)) continue;
				var object=new Type(name).setSchemaName(schema);
				var labels=new ArrayList<String>();
				try(var enumSql=c.prepareStatement("SELECT enumlabel FROM pg_catalog.pg_enum WHERE enumtypid=?::oid ORDER BY enumsortorder")) {
					enumSql.setString(1,rows.getString(3));
					try(var values=enumSql.executeQuery()) {while(values.next()) labels.add("'"+values.getString(1).replace("'","''")+"'");}
				}
				if(labels.isEmpty()) throw new SQLException("CockroachDB enum has no labels: "+name);
				object.setDefinition("CREATE TYPE "+CockroachMetadata.fullName(schema,name)+" AS ENUM ("+String.join(",",labels)+")");
				result.add(object);
			}
		} catch(SQLException e) { throw new IllegalStateException("Cannot read CockroachDB Type metadata",e); }
		return result;
	}

	@Override protected com.sqlapp.data.db.metadata.TypeColumnReader newColumnFactory() { return null; }
}
