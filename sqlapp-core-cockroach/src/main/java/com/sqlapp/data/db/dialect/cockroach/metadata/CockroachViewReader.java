/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

public class CockroachViewReader extends PostgresViewReader {
	public CockroachViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Table>();
		try (var sql = c.createStatement();
				var rows = sql.executeQuery(
						"SELECT table_schema,table_name FROM information_schema.tables WHERE table_catalog=current_database() AND table_type='VIEW' ORDER BY table_schema,table_name")) {
			while (rows.next()) {
				String schema = rows.getString(1), name = rows.getString(2);
				if (!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context, "schemaName", schema)
						|| !CockroachMetadata.matches(context, "viewName", name))
					continue;
				var object = new View(name).setSchemaName(schema);
				object.setDefinition(CockroachMetadata.definition(c, "VIEW", schema, name));
				result.add(object);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB View metadata", e);
		}
		return result;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new CockroachColumnReader(getDialect());
	}
}
