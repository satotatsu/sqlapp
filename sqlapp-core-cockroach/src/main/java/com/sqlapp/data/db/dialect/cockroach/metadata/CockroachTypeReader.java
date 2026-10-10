/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

public class CockroachTypeReader extends TypeReader {
	public CockroachTypeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Type> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Type>();
		boolean multiRegion;
		try (var sql = c.createStatement();
				var rows = sql.executeQuery("SHOW REGIONS FROM DATABASE " + CockroachMetadata.quote(c.getCatalog()))) {
			multiRegion = rows.next();
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB database regions", e);
		}
		try (var sql = c.createStatement();
				var rows = sql.executeQuery(
						"SELECT n.nspname,t.typname,t.oid,e.enumlabel FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid=t.typnamespace LEFT JOIN pg_catalog.pg_enum e ON e.enumtypid=t.oid WHERE t.typtype='e' ORDER BY n.nspname,t.typname,e.enumsortorder")) {
			while (rows.next()) {
				String schema = rows.getString(1), name = rows.getString(2);
				// PRIMARY REGION creates this engine-owned type; replaying CREATE TYPE
				// duplicates it.
				if (multiRegion && "public".equals(schema) && "crdb_internal_region".equals(name))
					continue;
				if (!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context, "schemaName", schema)
						|| !CockroachMetadata.matches(context, "typeName", name))
					continue;
				Type object;
				if (!result.isEmpty() && name.equals(result.get(result.size()-1).getName())
						&& schema.equals(result.get(result.size()-1).getSchemaName())) {
					object = result.get(result.size()-1);
				} else {
					object = new Type(name).setSchemaName(schema);
					object.setDefinition("CREATE TYPE " + CockroachMetadata.fullName(schema, name) + " AS ENUM ()");
					result.add(object);
				}
				String label = rows.getString(4);
				if (label == null) throw new SQLException("CockroachDB enum has no labels: " + name);
				String ddl = String.join("\n", object.getDefinition());
				String prefix = ddl.substring(0, ddl.length()-1);
				object.setDefinition(prefix + (prefix.endsWith("(") ? "" : ",") + "'" + label.replace("'", "''") + "')");
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB Type metadata", e);
		}
		return result;
	}

	@Override
	protected com.sqlapp.data.db.metadata.TypeColumnReader newColumnFactory() {
		return null;
	}
}
