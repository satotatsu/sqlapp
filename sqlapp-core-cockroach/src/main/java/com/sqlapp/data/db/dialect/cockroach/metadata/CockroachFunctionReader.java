/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

/**
 * Complete executable definitions retain arguments/defaults and overload
 * identity.
 */
public class CockroachFunctionReader extends FunctionReader {
	public CockroachFunctionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected RoutineArgumentReader<?> newRoutineArgumentReader() {
		return null;
	}

	@Override
	protected List<Function> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Function>();
		try (var sql = c.createStatement();
				var rows = sql.executeQuery(
						"SELECT n.nspname,p.proname,p.oid,pg_get_functiondef(p.oid),pg_get_function_identity_arguments(p.oid) FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE p.prokind='f' AND n.nspname NOT IN ('pg_catalog','information_schema','crdb_internal') ORDER BY n.nspname,p.proname,p.oid")) {
			while (rows.next()) {
				String schema = rows.getString(1), name = rows.getString(2);
				if (!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context, "schemaName", schema)
						|| !CockroachMetadata.matches(context, "functionName", name))
					continue;
				var function = new Function(name).setSchemaName(schema)
						.setSpecificName(name + "(" + rows.getString(5) + ")");
				function.setDefinition(rows.getString(4));
				result.add(function);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB functions", e);
		}
		return result;
	}
}
