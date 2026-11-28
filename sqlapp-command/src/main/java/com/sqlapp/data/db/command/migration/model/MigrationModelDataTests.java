/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.sqlapp.data.db.command.migration.model.MigrationModelDataTest.Kind;
import com.sqlapp.data.db.command.migration.model.MigrationModelDataTest.Severity;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelPlan;
import com.sqlapp.jdbc.bulk.MigrationModelExecutor;
import com.sqlapp.jdbc.bulk.MigrationModelExecutorResolver;

/**
 * Runs data tests against built models. Only the number of failing rows is
 * reported, never their values; a test query that fails reports the error
 * category without the database message.
 */
public final class MigrationModelDataTests {
	private MigrationModelDataTests() {
	}

	public enum Status {
		PASS,
		/** Failing rows in a WARN test. */
		WARN,
		/** Failing rows in an ERROR test. */
		FAIL,
		/** The test query could not run. */
		ERROR,
		/** Not run because an earlier test query failed. */
		NOT_RUN
	}

	/**
	 * @param failingRows rows that break the rule; duplicated keys for UNIQUE; the
	 *                    number of rows of the model for ROW_COUNT
	 */
	public record Result(String id, String model, Kind kind, Severity severity, Status status, long failingRows,
			long elapsedMillis, String message) {
	}

	/** Results of one run. */
	public record Results(List<Result> tests) {
		public Results {
			tests = List.copyOf(tests);
		}

		/** True when no test failed or errored; warnings are allowed. */
		public boolean isSuccess() {
			return tests.stream().noneMatch(test -> test.status() == Status.FAIL || test.status() == Status.ERROR
					|| test.status() == Status.NOT_RUN);
		}

		public long count(final Status status) {
			return tests.stream().filter(test -> test.status() == status).count();
		}

		public String summary() {
			return "passed: " + count(Status.PASS) + ", warned: " + count(Status.WARN) + ", failed: "
					+ (count(Status.FAIL) + count(Status.ERROR))
					+ (count(Status.NOT_RUN) == 0 ? "" : ", not run: " + count(Status.NOT_RUN));
		}
	}

	/** Runs the tests of the given models, in model order. */
	public static Results run(final Connection connection, final MigrationModelPlan plan,
			final Collection<MigrationModel> models, final List<MigrationModelDataTest> tests) throws SQLException {
		final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
		final Set<String> selected = models.stream().map(model -> model.name().toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
		final List<Result> results = new ArrayList<>();
		boolean aborted = false;
		for (final MigrationModel model : models) {
			for (final MigrationModelDataTest test : tests) {
				if (!test.model().equalsIgnoreCase(model.name())
						|| !selected.contains(model.name().toLowerCase(Locale.ROOT))) {
					continue;
				}
				if (aborted) {
					// A failed statement can abort the transaction of the run.
					results.add(new Result(test.id(), model.name(), test.kind(), test.severity(), Status.NOT_RUN, 0, 0,
							"not run after a failed test query"));
					continue;
				}
				final Result result = run(connection, plan, executor, model, test);
				aborted = result.status() == Status.ERROR;
				results.add(result);
			}
		}
		return new Results(results);
	}

	private static Result run(final Connection connection, final MigrationModelPlan plan,
			final MigrationModelExecutor executor, final MigrationModel model, final MigrationModelDataTest test) {
		final long started = System.nanoTime();
		try {
			final String sql = query(plan, executor, model, test);
			final long count;
			try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
				resultSet.next();
				count = resultSet.getLong(1);
			}
			final long elapsed = (System.nanoTime() - started) / 1_000_000L;
			if (test.kind() == Kind.ROW_COUNT) {
				final boolean ok = (test.min() == null || count >= test.min())
						&& (test.max() == null || count <= test.max());
				return new Result(test.id(), model.name(), test.kind(), test.severity(),
						ok ? Status.PASS : failed(test), count, elapsed,
						ok ? null : "row count " + count + " is outside " + range(test));
			}
			return new Result(test.id(), model.name(), test.kind(), test.severity(),
					count == 0 ? Status.PASS : failed(test), count, elapsed,
					count == 0 ? null : count + (test.kind() == Kind.UNIQUE ? " duplicated keys" : " failing rows"));
		} catch (SQLException | RuntimeException e) {
			// The caller owns the transaction and rolls it back.
			return new Result(test.id(), model.name(), test.kind(), test.severity(), Status.ERROR, 0,
					(System.nanoTime() - started) / 1_000_000L, errorMessage(e));
		}
	}

	/**
	 * Category of a failed test query. The driver message is left out: it can quote
	 * the value that failed, such as a string that cannot be cast.
	 */
	static String errorMessage(final Exception e) {
		final StringBuilder message = new StringBuilder("test query failed (").append(e.getClass().getSimpleName());
		if (e instanceof SQLException sql) {
			if (sql.getSQLState() != null) {
				message.append(", SQLSTATE ").append(sql.getSQLState());
			}
			if (sql.getErrorCode() != 0) {
				message.append(", error code ").append(sql.getErrorCode());
			}
		}
		return message.append("); run the test query in the engine to see the database message").toString();
	}

	private static Status failed(final MigrationModelDataTest test) {
		return test.severity() == Severity.WARN ? Status.WARN : Status.FAIL;
	}

	private static String range(final MigrationModelDataTest test) {
		return "[" + (test.min() == null ? "" : test.min()) + ", " + (test.max() == null ? "" : test.max()) + "]";
	}

	/** Counting query of the test: failing rows, or all rows for ROW_COUNT. */
	static String query(final MigrationModelPlan plan, final MigrationModelExecutor executor,
			final MigrationModel model, final MigrationModelDataTest test) {
		final String relation = executor.relation(model);
		final String failing = switch (test.kind()) {
		case NOT_NULL -> "SELECT * FROM " + relation + " WHERE " + test.columns().stream()
				.map(column -> executor.identifier(column) + " IS NULL").collect(Collectors.joining(" OR "));
		case UNIQUE -> {
			final String columns = test.columns().stream().map(executor::identifier).collect(Collectors.joining(", "));
			// One row per duplicated key; a key with a NULL is not compared, as in a
			// UNIQUE constraint.
			yield "SELECT " + columns + " FROM " + relation + " WHERE " + test.columns().stream()
					.map(column -> executor.identifier(column) + " IS NOT NULL").collect(Collectors.joining(" AND "))
					+ " GROUP BY " + columns + " HAVING count(*) > 1";
		}
		case ACCEPTED_VALUES -> {
			// NULL is not tested, as in dbt; add a notNull test to reject it.
			final String column = executor.identifier(test.columns().getFirst());
			yield "SELECT * FROM " + relation + " WHERE " + column + " IS NOT NULL AND " + column + " NOT IN ("
					+ test.values().stream().map(MigrationModelDataTests::literal).collect(Collectors.joining(", "))
					+ ")";
		}
		case RELATIONSHIPS -> {
			final String parent = reference(plan, executor, test.to());
			final List<String> on = new ArrayList<>();
			for (int i = 0; i < test.columns().size(); i++) {
				on.add("parent_rows." + executor.identifier(test.fields().get(i)) + " = child_rows."
						+ executor.identifier(test.columns().get(i)));
			}
			yield "SELECT * FROM " + relation + " AS child_rows WHERE "
					+ test.columns().stream()
							.map(column -> "child_rows." + executor.identifier(column) + " IS NOT NULL")
							.collect(Collectors.joining(" AND "))
					+ " AND NOT EXISTS (SELECT 1 FROM " + parent + " AS parent_rows WHERE " + String.join(" AND ", on)
					+ ")";
		}
		case EXPRESSION -> "SELECT * FROM " + relation + " WHERE (" + test.expression() + ") IS NOT TRUE";
		case ROW_COUNT -> null;
		case SQL -> MigrationModelRunner.resolveSql(plan, executor, new MigrationModel(model.name(), model.schemaName(),
				stripEnd(test.sql()), MigrationModel.Materialization.VIEW, model.parameters()));
		};
		return failing == null ? "SELECT count(*) FROM " + relation
				: "SELECT count(*) FROM (\n" + failing + "\n) AS failing_rows";
	}

	/** Relation of a model, or of a source when no model has the name. */
	private static String reference(final MigrationModelPlan plan, final MigrationModelExecutor executor,
			final String name) {
		final boolean model = plan.models().stream().anyMatch(candidate -> candidate.name().equalsIgnoreCase(name));
		final boolean source = plan.sources().values().stream()
				.anyMatch(candidate -> candidate.name().equalsIgnoreCase(name));
		if (model && source) {
			throw new IllegalArgumentException(name + " is both a model and a source; rename one of them.");
		}
		return model ? executor.relation(plan.model(name)) : executor.sourceRelation(plan.source(name));
	}

	private static String literal(final Object value) {
		if (value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		return "'" + value.toString().replace("'", "''") + "'";
	}

	private static String stripEnd(final String sql) {
		String value = sql.strip();
		while (value.endsWith(";")) {
			value = value.substring(0, value.length() - 1).stripTrailing();
		}
		return value;
	}
}
