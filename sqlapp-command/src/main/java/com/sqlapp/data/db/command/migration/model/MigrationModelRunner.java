/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.command.migration.verification.MigrationTransformationTestResult;
import com.sqlapp.data.db.command.migration.verification.MigrationTransformationTestRunner;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelPlan;
import com.sqlapp.data.schemas.migration.MigrationModelReferences;
import com.sqlapp.data.schemas.migration.MigrationModelReferences.Kind;
import com.sqlapp.data.schemas.migration.MigrationModelUnitTest;
import com.sqlapp.data.schemas.migration.MigrationTransformationTest;
import com.sqlapp.jdbc.bulk.MigrationModelExecutor;
import com.sqlapp.jdbc.bulk.MigrationModelExecutorResolver;

/**
 * Builds models in dependency order inside the connected engine and runs model
 * unit tests against inline fixtures.
 */
public final class MigrationModelRunner {

	private MigrationModelRunner() {
	}

	public enum Status {
		SUCCESS, FAILED,
		/** Not built because an upstream model failed or was skipped. */
		SKIPPED
	}

	public record ModelResult(String name, MigrationModel.Materialization materialization, Status status,
			long elapsedMillis, String message) {
	}

	public record Result(List<ModelResult> models) {
		public Result {
			models = List.copyOf(models);
		}

		public boolean isSuccess() {
			return models.stream().allMatch(model -> model.status() == Status.SUCCESS);
		}

		public List<ModelResult> failures() {
			return models.stream().filter(model -> model.status() == Status.FAILED).toList();
		}
	}

	/** Builds every model of the plan. */
	public static Result run(final Connection connection, final MigrationModelPlan plan) throws SQLException {
		return run(connection, plan, plan.models());
	}

	/**
	 * Builds the selected models in the given order. Each model is committed
	 * separately; a failed model is rolled back, its downstream models are skipped
	 * and unrelated models continue.
	 */
	public static Result run(final Connection connection, final MigrationModelPlan plan,
			final List<MigrationModel> selected) throws SQLException {
		Objects.requireNonNull(plan, "plan");
		final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
		final boolean autoCommit = connection.getAutoCommit();
		final List<ModelResult> results = new ArrayList<>();
		final Set<String> unavailable = new LinkedHashSet<>();
		connection.setAutoCommit(false);
		try {
			for (final MigrationModel model : selected) {
				final List<String> blocked = plan.dependencies(model.name()).stream().filter(unavailable::contains)
						.toList();
				if (!blocked.isEmpty()) {
					unavailable.add(model.name());
					results.add(new ModelResult(model.name(), model.materialization(), Status.SKIPPED, 0,
							"Upstream model was not built: " + String.join(", ", blocked)));
					continue;
				}
				final long started = System.nanoTime();
				try {
					executor.materialize(connection, model, resolveSql(plan, executor, model));
					connection.commit();
					results.add(new ModelResult(model.name(), model.materialization(), Status.SUCCESS,
							elapsedMillis(started), null));
				} catch (SQLException | RuntimeException failure) {
					try {
						connection.rollback();
					} catch (SQLException rollbackFailure) {
						failure.addSuppressed(rollbackFailure);
					}
					unavailable.add(model.name());
					results.add(new ModelResult(model.name(), model.materialization(), Status.FAILED,
							elapsedMillis(started), message(failure)));
				}
			}
		} finally {
			connection.setAutoCommit(autoCommit);
		}
		return new Result(results);
	}

	/** Model SQL with every reference replaced by its engine relation. */
	public static String resolveSql(final MigrationModelPlan plan, final MigrationModelExecutor executor,
			final MigrationModel model) {
		return MigrationModelReferences.resolve(model.sql(),
				reference -> reference.kind() == Kind.REF ? executor.relation(plan.model(reference.name()))
						: executor.sourceRelation(plan.source(reference.name())));
	}

	/**
	 * Runs unit tests without creating or reading any model relation. Every model
	 * and source referenced by the tested model must have a fixture.
	 */
	public static List<MigrationTransformationTestResult> test(final Connection connection,
			final MigrationModelPlan plan, final List<MigrationModelUnitTest> unitTests) throws SQLException {
		Objects.requireNonNull(plan, "plan");
		if (unitTests.isEmpty()) {
			return List.of();
		}
		final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
		final List<MigrationTransformationTest> tests = new ArrayList<>();
		for (final MigrationModelUnitTest unitTest : unitTests) {
			final MigrationModel model = plan.model(unitTest.model());
			final java.util.Map<String, String> fixtures = new java.util.LinkedHashMap<>();
			final String body = MigrationModelReferences.resolve(model.sql(), reference -> {
				final String name = reference.kind() == Kind.REF ? plan.model(reference.name()).name()
						: plan.source(reference.name()).name();
				final MigrationModelUnitTest.Fixture fixture = unitTest.given().entrySet().stream()
						.filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(java.util.Map.Entry::getValue)
						.findFirst().orElseThrow(() -> new IllegalArgumentException("Unit test " + unitTest.id()
								+ " has no fixture for " + name + ", which model " + model.name() + " reads."));
				final String identifier = executor.identifier(name);
				fixtures.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT),
						identifier + " AS " + executor.fixtureRelation(fixture));
				return identifier;
			});
			final List<String> unused = unitTest.given().keySet().stream()
					.filter(name -> !fixtures.containsKey(name.toLowerCase(java.util.Locale.ROOT))).toList();
			if (!unused.isEmpty()) {
				throw new IllegalArgumentException("Unit test " + unitTest.id() + " has fixtures that model "
						+ model.name() + " does not read: " + String.join(", ", unused));
			}
			final String sql = withFixtures(body, fixtures.values());
			tests.add(new MigrationTransformationTest(unitTest.id(), sql, model.parameters(), unitTest.expectedRows(),
					unitTest.ordered()));
		}
		return MigrationTransformationTestRunner.run(connection, tests);
	}

	/**
	 * Supplies the fixtures as common table expressions named after the references,
	 * so aliases and column qualifiers in the model keep working.
	 */
	static String withFixtures(final String sql, final java.util.Collection<String> fixtures) {
		if (fixtures.isEmpty()) {
			return sql;
		}
		final String list = String.join(",\n", fixtures);
		final int start = statementStart(sql);
		final java.util.regex.Matcher with = WITH.matcher(sql).region(start, sql.length());
		if (with.lookingAt()) {
			return sql.substring(0, with.end()) + list + ",\n" + sql.substring(with.end());
		}
		return sql.substring(0, start) + "WITH " + list + "\n" + sql.substring(start);
	}

	private static final java.util.regex.Pattern WITH = java.util.regex.Pattern.compile("WITH\\s+(?:RECURSIVE\\s+)?",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	/** Index of the first token after leading whitespace and comments. */
	private static int statementStart(final String sql) {
		int i = 0;
		while (i < sql.length()) {
			if (Character.isWhitespace(sql.charAt(i))) {
				i++;
			} else if (sql.startsWith("--", i)) {
				final int end = sql.indexOf('\n', i);
				i = end < 0 ? sql.length() : end + 1;
			} else if (sql.startsWith("/*", i)) {
				final int end = sql.indexOf("*/", i + 2);
				i = end < 0 ? sql.length() : end + 2;
			} else {
				break;
			}
		}
		return i;
	}

	private static long elapsedMillis(final long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000L;
	}

	private static String message(final Exception failure) {
		final String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getName() : message;
	}
}
