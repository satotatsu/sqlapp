/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * Reads each actual CHECK expression and its own name from stored CREATE DDL.
 */
public class SqliteCheckConstraintReader extends CheckConstraintReader {
	public SqliteCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<CheckConstraint> result = new ArrayList<>();
		try (var statement = SqliteMetadataQueries.prepare(connection, context, "m.name", "", "");
				var rs = statement.executeQuery()) {
			while (rs.next()) {
				String table = rs.getString("table_name");
				int sequence = 0;
				for (String fragment : SqliteColumnReader.splitColumnDefinitions(rs.getString("table_sql"))) {
					for (var check : SqliteDdl.checks(fragment)) {
						sequence++;
						var constraint = new CheckConstraint(
								check.name() == null ? "sqlite_check_" + table + "_" + sequence : check.name(),
								check.expression());
						constraint.setDialect(getDialect());
						constraint.setCatalogName(getCatalogName(context));
						constraint.setSchemaName(getSchemaName(context));
						constraint.setTableName(table);
						result.add(constraint);
					}
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	static List<String> extractExpressions(String definition) {
		return SqliteDdl.checks(definition).stream().map(SqliteDdl.CheckDeclaration::expression).toList();
	}
}
