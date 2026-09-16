/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.migration.MigrationDataTest;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.SqlParser;

/** Executes failure-row queries using sqlapp's existing SQL comment templates. */
public final class MigrationDataTestRunner {

	private MigrationDataTestRunner() {
	}

	public static List<MigrationDataTestResult> run(final Connection connection,
			final List<MigrationDataTest> tests) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final List<MigrationDataTestResult> results = new ArrayList<>();
		for (final MigrationDataTest test : tests) {
			results.add(run(connection, dialect, test));
		}
		return List.copyOf(results);
	}

	static MigrationDataTestResult run(final Connection connection, final Dialect dialect,
			final MigrationDataTest test) throws SQLException {
		final String sql = sql(dialect, test);
		final ParametersContext context = new ParametersContext();
		context.putAll(test.parameters());
		final long[] failures = { 0L };
		final List<List<Object>> samples = new ArrayList<>();
		final var handler = new JdbcQueryHandler(SqlParser.getInstance().parse(dialect, sql),
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						failures[0]++;
						if (samples.size() < test.maximumSamples()) {
							final ResultSetMetaData metadata = rs.getNativeObject().getMetaData();
							final List<Object> row = new ArrayList<>(metadata.getColumnCount());
							for (int i = 1; i <= metadata.getColumnCount(); i++) {
								row.add(rs.getObject(i));
							}
							samples.add(Collections.unmodifiableList(row));
						}
					}
				});
		handler.execute(connection, context);
		return new MigrationDataTestResult(test.id(), test.type(), test.severity(), status(test, failures[0]),
				failures[0], samples);
	}

	private static MigrationDataTestResult.Status status(final MigrationDataTest test, final long failures) {
		if (failures > test.errorIf() && test.severity() == MigrationDataTest.Severity.ERROR) {
			return MigrationDataTestResult.Status.ERROR;
		}
		if (failures > test.warnIf()) {
			return MigrationDataTestResult.Status.WARN;
		}
		return MigrationDataTestResult.Status.PASS;
	}

	private static String sql(final Dialect dialect, final MigrationDataTest test) {
		if (test.type() == MigrationDataTest.Type.CUSTOM_SQL) {
			return test.sql();
		}
		final String table = dialect.getObjectFullName(test.catalogName(), test.schemaName(), test.tableName());
		final List<String> columns = test.columns().stream().map(dialect::quote).toList();
		return switch (test.type()) {
		case NOT_NULL -> "SELECT " + columns.getFirst() + " FROM " + table + " WHERE " + columns.getFirst()
				+ " IS NULL";
		case UNIQUE -> "SELECT " + String.join(", ", columns) + ", COUNT(*) FROM " + table + " GROUP BY "
				+ String.join(", ", columns) + " HAVING COUNT(*) > 1";
		default -> throw new IllegalArgumentException("Test type requires additional arguments: " + test.type());
		};
	}
}
