/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

public class CockroachMviewReader extends MviewReader {
	public CockroachMviewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Table>();
		var query = new StringBuilder("""
				SELECT v.schemaname, v.matviewname, v.definition, c.oid, v.matviewowner
				FROM pg_catalog.pg_matviews v
				JOIN pg_catalog.pg_namespace n ON n.nspname = v.schemaname
				JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid
					AND c.relname = v.matviewname AND c.relkind = 'm'
				WHERE 1=1
				""");
		var parameters = new ArrayList<String>();
		CockroachMetadata.addFilter(query, parameters, "v.schemaname", context.get("schemaName"));
		CockroachMetadata.addFilter(query, parameters, "v.matviewname", context.get(getNameLabel()));
		query.append(" ORDER BY v.schemaname, v.matviewname");
		try (var sql = connection.prepareStatement(query.toString())) {
			for (int i = 0; i < parameters.size(); i++)
				sql.setString(i + 1, parameters.get(i));
			try (var rows = sql.executeQuery()) {
				while (rows.next()) {
					String schema = rows.getString(1), name = rows.getString(2);
					if (!CockroachMetadata.userSchema(schema)
							|| !CockroachMetadata.matches(context, "schemaName", schema)
							|| !CockroachMetadata.matches(context, getNameLabel(), name))
						continue;
					String statement = rows.getString(3);
					if (statement == null || statement.isBlank())
						throw new SQLException(
								"No CockroachDB materialized view query: " + CockroachMetadata.fullName(schema, name));
					var view = new Mview(name).setOwnerName(rows.getString(5));
					view.setSchemaName(schema).setStatement(statement).setId(rows.getString(4));
					result.add(view);
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB materialized views", e);
		}
		return result;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new CockroachColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new CockroachIndexReader(getDialect(), false);
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
