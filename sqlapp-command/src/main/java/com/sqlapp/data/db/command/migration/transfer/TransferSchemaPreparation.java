/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Read-only planning of missing ordinary tables. DDL comes exclusively from the
 * target dialect.
 */
public final class TransferSchemaPreparation {
	/**
	 * Controlled diagnostic without JDBC errors, connection strings or raw driver
	 * causes.
	 */
	public static final class ValidationException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		private ValidationException(String message) {
			super(message);
		}
	}

	public record Statement(String taskId, String sql) {
	}

	public record Plan(String fingerprint, List<Statement> statements, List<String> warnings) {
		public Plan(String fingerprint, List<Statement> statements) {
			this(fingerprint, statements, List.of());
		}

		public Plan {
			statements = List.copyOf(statements);
			warnings = List.copyOf(warnings);
		}
	}

	/**
	 * A failed statement may have taken effect even when it did not return
	 * successfully.
	 */
	public record Progress(List<Statement> planned, int completedStatements, Integer attemptedStatement,
			List<String> warnings) {
		public Progress(List<Statement> planned, int completedStatements, Integer attemptedStatement) {
			this(planned, completedStatements, attemptedStatement, List.of());
		}

		public Progress {
			planned = List.copyOf(planned);
			warnings = List.copyOf(warnings);
		}
	}

	private TransferSchemaPreparation() {
	}

	/**
	 * Shared DDL boundary; observers retain an uncertain attempt even when execute
	 * or close fails.
	 */
	static Progress execute(Connection target, Plan plan, java.util.function.Consumer<Progress> observer)
			throws java.sql.SQLException {
		int completed = 0;
		Progress progress = new Progress(plan.statements(), 0, null, plan.warnings());
		observer.accept(progress);
		for (int index = 0; index < plan.statements().size(); index++) {
			progress = new Progress(plan.statements(), completed, index + 1, plan.warnings());
			observer.accept(progress);
			try (var statement = target.createStatement()) {
				statement.execute(plan.statements().get(index).sql());
			}
			progress = new Progress(plan.statements(), ++completed, null, plan.warnings());
			observer.accept(progress);
		}
		return progress;
	}

	static Plan plan(Connection connection, Schema sourceSchema, BulkMigrationJobPlan data,
			TransferJobConfiguration.SchemaMode mode, TransferJobConfiguration.CompatibilityPolicy policy)
			throws Exception {
		final var warnings = new ArrayList<String>();
		if (!Objects.equals(sourceSchema.getProductName(), connection.getMetaData().getDatabaseProductName()))
			compatibility(policy, warnings,
					"Source and target products differ. DDL/types are generated from the shared Schema with the target dialect; vendor semantics require review and testing.");
		if (mode == TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING) {
			BulkMigrationTargetValidator.validate(connection, data);
			return result(data, List.of(), warnings, mode);
		}
		final Table first = data.getTasks().getFirst().getEffectiveTargetTable();
		boolean schemaExists = false;
		final var availableSchemas = new java.util.TreeSet<String>();
		try (var schemas = connection.getMetaData().getSchemas()) {
			while (schemas.next()) {
				if (schemas.getString("TABLE_CATALOG") != null && first.getCatalogName() != null
						&& !Objects.equals(first.getCatalogName(), schemas.getString("TABLE_CATALOG")))
					continue;
				final String name = schemas.getString("TABLE_SCHEM");
				if (name != null) {
					availableSchemas.add(name);
					if (availableSchemas.size() > 10)
						availableSchemas.pollLast();
				}
				if (Objects.equals(first.getSchemaName(), name))
					schemaExists = true;
			}
		}
		if (!schemaExists)
			throw new ValidationException("Target schema " + diagnosticName(first.getSchemaName())
					+ " was not found in the selected catalog. "
					+ "CREATE_MISSING prepares tables inside an existing schema. Set targetSchema to an existing schema "
					+ "with its exact name/case, or create the schema separately with the intended owner and privileges, then review again. "
					+ "Existing schemas (up to 10): "
					+ (availableSchemas.isEmpty() ? "none reported by JDBC metadata"
							: availableSchemas.stream().limit(10).map(TransferSchemaPreparation::diagnosticName)
									.collect(java.util.stream.Collectors.joining(", ")))
					+ ".");
		final var names = new ArrayList<String>();
		try (var objects = connection.getMetaData().getTables(first.getCatalogName(), null, "%", null)) {
			while (objects.next())
				if (Objects.equals(first.getSchemaName(), objects.getString("TABLE_SCHEM")))
					names.add(objects.getString("TABLE_NAME"));
		}
		final var existing = new LinkedHashSet<String>();
		final var missing = new ArrayList<BulkMigrationJobTask>();
		for (var task : data.getTasks()) {
			final String name = task.getEffectiveTargetTable().getName();
			final var matches = names.stream().filter(actual -> actual.equalsIgnoreCase(name)).toList();
			if (matches.isEmpty())
				missing.add(task);
			else if (matches.size() == 1 && matches.getFirst().equals(name))
				existing.add(task.getTaskId());
			else
				throw new ValidationException(
						"Target identifier collides by case or is ambiguous. Resolve it before Schema preparation.");
		}
		if (!existing.isEmpty())
			BulkMigrationTargetValidator.validate(connection, data.selectTasks(existing));
		final var dialect = TransferJobPreparationService.dialect(connection);
		final var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setOutputCommit(false);
		final var remapped = new LinkedHashMap<String, Table>();
		for (var task : data.getTasks())
			remapped.put(task.getEffectiveSourceTable().getName(), task.getEffectiveSourceTable().clone()
					.setCatalogName(first.getCatalogName()).setSchemaName(first.getSchemaName()));
		final var statements = new ArrayList<Statement>();
		for (var task : missing) {
			final var source = task.getEffectiveSourceTable();
			ordinary(source, policy, warnings);
			final var target = remapped.get(source.getName());
			for (var fk : target.getConstraints().getForeignKeyConstraints()) {
				if (fk.getRelatedTableSchemaName() != null
						&& !Objects.equals(fk.getRelatedTableSchemaName(), sourceSchema.getName())
						|| data.getTasks().stream().noneMatch(candidate -> candidate.getEffectiveSourceTable().getName()
								.equals(fk.getRelatedTableName())))
					throw new ValidationException(
							"Select every referenced table from the same source schema before creating missing tables.");
				fk.setRelatedTableSchemaName(first.getSchemaName());
				final var parent = remapped.get(fk.getRelatedTableName());
				final var columns = fk.getRelatedColumns().stream().map(ref -> parent.getColumns().get(ref.getName()))
						.toArray(com.sqlapp.data.schemas.Column[]::new);
				// addAll retains explicit referenced columns; add(Column) may resolve against
				// the child table.
				fk.getRelatedColumns().clear();
				fk.getRelatedColumns().addAll(columns);
			}
			final var operations = registry.createSql(target, SqlType.CREATE);
			if (operations.isEmpty())
				throw new ValidationException(
						"Target dialect cannot generate CREATE TABLE. Prepare tables separately and use REQUIRE_EXISTING.");
			for (var operation : operations) {
				if (operation.getSqlText() == null || operation.getSqlText().isBlank()
						|| operation.getSqlType() != SqlType.CREATE)
					throw new ValidationException(
							"Missing-table preparation requires executable CREATE operations only. Prepare tables separately and use REQUIRE_EXISTING.");
				statements.add(new Statement(task.getTaskId(), operation.getSqlText()));
			}
		}
		return result(data, statements, warnings, mode);
	}

	private static Plan result(BulkMigrationJobPlan data, List<Statement> statements, List<String> warnings,
			TransferJobConfiguration.SchemaMode mode) {
		if (mode == TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING && warnings.isEmpty())
			return new Plan(data.getFingerprint(), statements);
		final String canonical = new com.sqlapp.util.JsonConverter()
				.toJsonString(List.of(mode.name(), data.getFingerprint(), statements, warnings));
		return new Plan(
				"sha256:" + com.sqlapp.util.MessageDigests.SHA256
						.checksumAsString(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				statements, warnings);
	}

	private static String diagnosticName(String name) {
		if (name == null)
			return "(unspecified)";
		final String printable = name.replaceAll("\\p{Cntrl}", " ");
		return "'" + (printable.length() > 128 ? printable.substring(0, 128) + "…" : printable).replace("'", "''")
				+ "'";
	}

	private static void compatibility(TransferJobConfiguration.CompatibilityPolicy policy, List<String> warnings,
			String message) {
		if (policy == TransferJobConfiguration.CompatibilityPolicy.ERROR)
			throw new ValidationException(message);
		warnings.add(message);
	}

	private static void ordinary(Table table, TransferJobConfiguration.CompatibilityPolicy policy,
			List<String> warnings) {
		if (table.getTableType() == Table.TableType.Temporary || table.getPartitioning() != null
				|| table.getPartitionParent() != null || !table.getInherits().isEmpty())
			compatibility(policy, warnings, "Table " + table.getName()
					+ ": temporary, partitioned or inherited structure; inspect generated DDL for retained and omitted vendor properties.");
		for (var column : table.getColumns())
			if (column.isIdentity() || column.getDefaultValue() != null || column.getFormula() != null
					|| column.getDomainName() != null)
				compatibility(policy, warnings, "Table " + table.getName() + ", column " + column.getName()
						+ ": identity/default/generated/domain semantics require testing of DDL, dependencies and subsequent inserts.");
	}
}
