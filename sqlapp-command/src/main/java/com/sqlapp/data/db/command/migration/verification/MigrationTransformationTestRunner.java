/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;


import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.migration.MigrationTransformationTest;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * Runs transformation queries against small inline fixtures without
 * materializing a model.
 */
public final class MigrationTransformationTestRunner {

	private MigrationTransformationTestRunner() {
	}

	public static List<MigrationTransformationTestResult> run(final Connection connection,
			final List<MigrationTransformationTest> tests) throws SQLException {
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		final List<MigrationTransformationTestResult> results = new ArrayList<>();
		for (final MigrationTransformationTest test : tests) {
			final ParametersContext context = new ParametersContext();
			context.putAll(test.parameters());
			final List<List<String>> actual = new ArrayList<>();
			new JdbcQueryHandler(SqlParser.getInstance().parse(dialect, test.sql()), new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs) throws SQLException {
					final ResultSetMetaData metadata = rs.getNativeObject().getMetaData();
					final List<String> row = new ArrayList<>(metadata.getColumnCount());
					for (int i = 1; i <= metadata.getColumnCount(); i++) {
						row.add(canonical(rs.getObject(i)));
					}
					actual.add(Collections.unmodifiableList(row));
				}
			}).execute(connection, context);
			final List<List<String>> expected = new ArrayList<>(test.expectedRows());
			if (!test.ordered()) {
				final Comparator<List<String>> comparator = Comparator
						.comparing(MigrationTransformationTestRunner::key);
				expected.sort(comparator);
				actual.sort(comparator);
			}
			results.add(new MigrationTransformationTestResult(test.id(), expected.equals(actual), expected, actual));
		}
		return List.copyOf(results);
	}

	private static String canonical(final Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof TemporalAccessor) {
			return value.toString();
		}
		return String.valueOf(value);
	}

	private static String key(final List<String> row) {
		return row.stream().map(value -> value == null ? "<null>" : value.length() + ":" + value).reduce("",
				(left, right) -> left + "|" + right);
	}
}
