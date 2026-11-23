/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * Builds models in dependency order inside the connected engine, checks them
 * with data tests in the same transaction and runs model unit tests against
 * inline fixtures.
 */
public final class MigrationModelRunner {

	private MigrationModelRunner() {
	}

	public enum Status {
		/** Built and committed. */
		SUCCESS, FAILED,
		/** Not built because an earlier model failed. */
		SKIPPED,
		/** Built, then rolled back because a later model or a data test failed. */
		ROLLED_BACK
	}

	public record ModelResult(String name, MigrationModel.Materialization materialization, Status status,
			long elapsedMillis, String message) {
	}

	/**
	 * @param models    models in build order
	 * @param dataTests data test results, or null when the build failed or no tests
	 *                  were given
	 * @param committed whether the models were committed
	 */
	public record Result(List<ModelResult> models, MigrationModelDataTests.Results dataTests, boolean committed) {
		public Result {
			models = List.copyOf(models);
		}

		public boolean isSuccess() {
			return committed;
		}

		public List<ModelResult> failures() {
			return models.stream().filter(model -> model.status() == Status.FAILED).toList();
		}
	}

	/**
	 * @param fullRefresh rebuild incremental models from all rows
	 * @param dataTests   data tests to run before committing
	 */
	public record Options(boolean fullRefresh, List<MigrationModelDataTest> dataTests) {
		public static final Options DEFAULT = new Options(false, List.of());

		public Options {
			dataTests = dataTests == null ? List.of() : List.copyOf(dataTests);
		}
	}

	/** Builds every model of the plan. */
	public static Result run(final Connection connection, final MigrationModelPlan plan) throws SQLException {
		return run(connection, plan, plan.models(), Options.DEFAULT);
	}

	public static Result run(final Connection connection, final MigrationModelPlan plan,
			final List<MigrationModel> selected) throws SQLException {
		return run(connection, plan, selected, Options.DEFAULT);
	}

	/**
	 * Builds the selected models in dependency order and then runs every given data
	 * test in the same transaction, so a test sees every model of the run,
	 * including models built after the tested one. The transaction is committed
	 * only when every model was built and no ERROR test failed; otherwise every
	 * model of the run, including incremental rows and their recorded fingerprints,
	 * is rolled back. The first failed model stops the build.
	 */
	public static Result run(final Connection connection, final MigrationModelPlan plan,
			final List<MigrationModel> selected, final Options options) throws SQLException {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(options, "options");
		final MigrationModelExecutor executor = MigrationModelExecutorResolver.resolve(connection);
		final boolean autoCommit = connection.getAutoCommit();
		final List<ModelResult> results = new ArrayList<>();
		MigrationModelDataTests.Results dataTests = null;
		boolean committed = false;
		connection.setAutoCommit(false);
		try {
			String failed = null;
			for (final MigrationModel model : selected) {
				if (failed != null) {
					results.add(new ModelResult(model.name(), model.materialization(), Status.SKIPPED, 0,
							"Not built: model " + failed + " failed and the run is rolled back."));
					continue;
				}
				final long started = System.nanoTime();
				try {
					final String note = build(connection, plan, executor, model, options.fullRefresh());
					results.add(new ModelResult(model.name(), model.materialization(), Status.SUCCESS,
							elapsedMillis(started), note));
				} catch (SQLException | RuntimeException failure) {
					failed = model.name();
					results.add(new ModelResult(model.name(), model.materialization(), Status.FAILED,
							elapsedMillis(started), message(failure)));
				}
			}
			if (failed == null && !options.dataTests().isEmpty()) {
				dataTests = MigrationModelDataTests.run(connection, plan, plan.models(), options.dataTests());
			}
			final String reason = failed != null ? "model " + failed + " failed"
					: dataTests != null && !dataTests.isSuccess() ? "data tests failed" : null;
			if (reason == null) {
				connection.commit();
				committed = true;
			} else {
				connection.rollback();
				for (int i = 0; i < results.size(); i++) {
					final ModelResult built = results.get(i);
					if (built.status() == Status.SUCCESS) {
						results.set(i, new ModelResult(built.name(), built.materialization(), Status.ROLLED_BACK,
								built.elapsedMillis(), "Rolled back: " + reason + "."));
					}
				}
			}
		} catch (SQLException | RuntimeException e) {
			try {
				connection.rollback();
			} catch (SQLException rollback) {
				e.addSuppressed(rollback);
			}
			throw e;
		} finally {
			connection.setAutoCommit(autoCommit);
		}
		return new Result(results, dataTests, committed);
	}

	/** Builds one model; returns a note for an incremental model. */
	private static String build(final Connection connection, final MigrationModelPlan plan,
			final MigrationModelExecutor executor, final MigrationModel model, final boolean fullRefresh)
			throws SQLException {
		final String sql = resolveSql(plan, executor, model);
		if (!model.isIncremental()) {
			executor.materialize(connection, model, sql);
			return null;
		}
		final String fingerprint = plan.lineageFingerprint(model.name());
		final MigrationModelExecutor.IncrementalState state = executor.incrementalState(connection, model);
		final boolean incremental;
		final String note;
		if (!state.exists()) {
			incremental = false;
			note = "built from all rows (no table yet)";
		} else if (fullRefresh) {
			incremental = false;
			note = "rebuilt from all rows (fullRefresh)";
		} else if (state.fingerprint() == null) {
			throw new IllegalStateException("The table of incremental model " + model.name()
					+ " has no lineage fingerprint, so its rows cannot be shown to come from the current definitions."
					+ " Run with fullRefresh to rebuild it from all rows.");
		} else if (!state.fingerprint().equals(fingerprint)) {
			throw new IllegalStateException("The definition of incremental model " + model.name()
					+ " or of a model or source it reads changed since its table was built."
					+ " Its rows would mix old and new definitions. Run with fullRefresh to rebuild it from all rows.");
		} else {
			incremental = true;
			note = "merged new rows";
		}
		executor.materializeIncremental(connection, model, sql, incremental, fingerprint);
		return note;
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
				if (reference.kind() == Kind.REF && name.equals(model.name()) && !unitTest.incremental()
						&& unitTest.given().keySet().stream().noneMatch(key -> key.equalsIgnoreCase(name))) {
					// The self reference sits in the isIncremental block, which is not
					// rendered when the test builds from all rows.
					return executor.identifier(name);
				}
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
			final java.util.Map<String, Object> parameters = new java.util.HashMap<>(model.parameters());
			if (model.isIncremental()) {
				parameters.put(MigrationModel.INCREMENTAL_PARAMETER, unitTest.incremental());
			}
			tests.add(new MigrationTransformationTest(unitTest.id(), sql, parameters, unitTest.expectedRows(),
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
