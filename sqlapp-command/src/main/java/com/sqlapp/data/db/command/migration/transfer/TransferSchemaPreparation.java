/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.sql.Connection;
import java.util.*;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Read-only planning of missing tables and optional selected-table differences.
 * Schema SQL comes exclusively from the target dialect.
 */
public final class TransferSchemaPreparation {
	/**
	 * Controlled diagnostic without JDBC errors, connection strings or raw driver
	 * causes.
	 */
	public static final class ValidationException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		ValidationException(String message) {
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
	 * Shared Schema SQL boundary; observers retain an uncertain attempt even when
	 * execute or close fails.
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
		return plan(connection, sourceSchema, data, mode, policy, null);
	}

	static Plan plan(Connection connection, Schema sourceSchema, BulkMigrationJobPlan data,
			TransferJobConfiguration.SchemaMode mode, TransferJobConfiguration.CompatibilityPolicy policy,
			TransferNameMappingProjection.Projection projection) throws Exception {
		if (projection != null) {
			final var warnings = new ArrayList<String>();
			if (projection.renamedIdentifiers() && mode != TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING)
				mappingExpressions(policy, warnings);
			final var selections = data.getTasks().stream()
					.map(task -> new Selection(task.getTaskId(),
							projection.bySourceName().get(task.getEffectiveSourceTable().getName()),
							task.getEffectiveTargetTable()))
					.toList();
			final var base = plan(connection, projection.schema(), selections, data.getFingerprint(), mode, policy,
					data);
			warnings.addAll(base.warnings());
			return result(base.fingerprint(), base.statements(), warnings, mode);
		}
		return plan(
				connection, sourceSchema, data.getTasks().stream().map(task -> new Selection(task.getTaskId(),
						task.getEffectiveSourceTable(), task.getEffectiveTargetTable())).toList(),
				data.getFingerprint(), mode, policy, data);
	}

	/**
	 * Canonical table inputs shared by schema-only and data-transfer preparation.
	 */
	static record Selection(String getTaskId, Table getEffectiveSourceTable, Table getEffectiveTargetTable) {
	}

	static Plan schemaOnly(Connection connection, Schema sourceSchema, List<Selection> tasks, String fingerprint,
			TransferJobConfiguration.CompatibilityPolicy policy) throws Exception {
		return plan(connection, sourceSchema, tasks, fingerprint, TransferJobConfiguration.SchemaMode.CREATE_MISSING,
				policy, null);
	}

	static Plan schemaOnly(Connection connection, Schema sourceSchema, List<Selection> tasks, String fingerprint,
			TransferJobConfiguration.CompatibilityPolicy policy, TransferJobConfiguration.SchemaMode mode)
			throws Exception {
		return plan(connection, sourceSchema, tasks, fingerprint, mode, policy, null);
	}

	private static Plan plan(Connection connection, Schema sourceSchema, List<Selection> tasks, String fingerprint,
			TransferJobConfiguration.SchemaMode mode, TransferJobConfiguration.CompatibilityPolicy policy,
			BulkMigrationJobPlan data) throws Exception {
		final var warnings = new ArrayList<String>();
		if (!Objects.equals(sourceSchema.getProductName(), connection.getMetaData().getDatabaseProductName()))
			compatibility(policy, warnings,
					"Source and target products differ. DDL/types are generated from the shared Schema with the target dialect; vendor semantics require review and testing.");
		// Counter state is independent of table creation and row-copy verification.
		if (data != null)
			for (String check : TransferGeneratedKeyChecks.required(data))
				compatibility(policy, warnings, check);
		if (mode == TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING) {
			BulkMigrationTargetValidator.validate(connection, data);
			return result(fingerprint, List.of(), warnings, mode);
		}
		final Table first = tasks.getFirst().getEffectiveTargetTable();
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
		final var missing = new ArrayList<Selection>();
		for (var task : tasks) {
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
		if (!existing.isEmpty() && data != null)
			BulkMigrationTargetValidator.validate(connection, data.selectTasks(existing));
		if (data == null) {
			compatibility(policy, warnings,
					"Schema-only scope: selected tables, constraints and indexes only. Views, independent sequences, routines, triggers and other schema objects are not copied. Review vendor-specific properties in generated DDL.");
			for (var task : tasks)
				if (existing.contains(task.getTaskId())
						&& mode != TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES)
					compatibility(policy, warnings,
							"Existing target table " + diagnosticName(task.getEffectiveTargetTable().getName())
									+ " is skipped without comparison or modification. Existing rows are untouched.");
		}
		final var dialect = TransferJobPreparationService.dialect(connection);
		final var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setOutputCommit(false);
		final var remapped = new LinkedHashMap<String, Table>();
		for (var task : tasks)
			remapped.put(task.getEffectiveSourceTable().getName(), task.getEffectiveSourceTable().clone()
					.setCatalogName(first.getCatalogName()).setSchemaName(first.getSchemaName()));
		final var statements = new ArrayList<Statement>();
		final var preparedTasks = mode == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES ? tasks : missing;
		for (var task : preparedTasks) {
			final var source = task.getEffectiveSourceTable();
			ordinary(source, policy, warnings);
			final var target = remapped.get(source.getName());
			if (mode == TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
					&& (target.getPrimaryKeyConstraint() == null
							|| target.getPrimaryKeyConstraint().getColumns().isEmpty())) {
				if (target.getColumns().stream()
						.anyMatch(column -> column.getName().equalsIgnoreCase(TransferKeylessChecks.GENERATED_COLUMN)))
					throw new ValidationException(
							"Generated target key SQLAPP_ROW_ID collides with a source column. Prepare a target with another generated key manually and use REQUIRE_EXISTING.");
				final var column = new com.sqlapp.data.schemas.Column(TransferKeylessChecks.GENERATED_COLUMN)
						.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setIdentity(true).setNotNull(true);
				target.getColumns().add(column);
				String name = "sqlapp_pk_"
						+ com.sqlapp.util.MessageDigests.SHA256.checksumAsString(java.util.List
								.of(Objects.toString(target.getCatalogName(), ""),
										Objects.toString(target.getSchemaName(), ""), target.getName())
								.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 20);
				if (target.getConstraints().get(name) != null)
					throw new ValidationException(
							"Generated target primary-key constraint name collides. Prepare the target separately and use REQUIRE_EXISTING.");
				target.setPrimaryKey(name, column);
				compatibility(policy, warnings, TransferKeylessChecks.GENERATED_TARGET_KEY + " Target table "
						+ target.getName()
						+ " will add BIGINT identity primary key SQLAPP_ROW_ID. Values are generated only in the target, excluded from source copy/verification and are not business keys or inferred FK mappings. Review dialect CREATE SQL.");
			}
			for (var fk : target.getConstraints().getForeignKeyConstraints()) {
				if (fk.getRelatedTableSchemaName() != null
						&& !Objects.equals(fk.getRelatedTableSchemaName(), sourceSchema.getName())
						|| tasks.stream().noneMatch(candidate -> candidate.getEffectiveSourceTable().getName()
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
		}
		if (mode == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES)
			return TransferSchemaDifferencePreparation.plan(connection, tasks, remapped, registry, fingerprint,
					warnings, policy);
		// Access and other table-local namespaces may repeat a constraint name.
		// Give repeated names stable target identities; retain source metadata.
		final var constraintNames = new HashMap<String, Integer>();
		final var indexNames = new HashMap<String, Integer>();
		for (var table : remapped.values())
			for (var constraint : table.getConstraints())
				if (constraint.getName() != null)
					constraintNames.merge(constraint.getName().toUpperCase(Locale.ROOT), 1, Integer::sum);
		for (var table : remapped.values())
			for (var index : table.getIndexes())
				if (index.getName() != null)
					indexNames.merge(index.getName().toUpperCase(Locale.ROOT), 1, Integer::sum);
		final var reservedNames = new HashSet<>(constraintNames.keySet());
		reservedNames.addAll(indexNames.keySet());
		for (var task : missing) {
			final var target = remapped.get(task.getEffectiveSourceTable().getName());
			for (var constraint : target.getConstraints())
				if (constraint.getName() != null
						&& constraintNames.get(constraint.getName().toUpperCase(Locale.ROOT)) > 1) {
					final String originalName = constraint.getName();
					final String name = targetObjectName(target, originalName, "sqlapp_", reservedNames);
					compatibility(policy, warnings,
							TransferSchemaChangeChecks.NAME_ADJUSTMENT + " Repeated source constraint name "
									+ diagnosticName(originalName) + " on table " + diagnosticName(target.getName())
									+ " is generated as " + name
									+ " in CREATE SQL. Constraint columns and rules are retained.");
					constraint.setName(name);
				}
			for (var index : target.getIndexes())
				if (index.getName() != null && indexNames.get(index.getName().toUpperCase(Locale.ROOT)) > 1) {
					final String originalName = index.getName();
					final String name = targetObjectName(target, originalName, "sqlapp_i_", reservedNames);
					compatibility(policy, warnings,
							TransferSchemaChangeChecks.NAME_ADJUSTMENT + " Repeated source index name "
									+ diagnosticName(originalName) + " on table " + diagnosticName(target.getName())
									+ " is generated as " + name
									+ " in CREATE SQL. Index columns, ordering and uniqueness are retained.");
					index.setName(name);
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
		return result(fingerprint, statements, warnings, mode);
	}

	private static String targetObjectName(Table table, String originalName, String prefix, Set<String> reserved)
			throws Exception {
		final String identity = String.join("\u0000", Objects.toString(table.getCatalogName(), ""),
				Objects.toString(table.getSchemaName(), ""), table.getName(), originalName);
		final String name = prefix + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 20);
		if (!reserved.add(name.toUpperCase(Locale.ROOT)))
			throw new ValidationException(
					"Generated target object name collides. Rename the source constraint or index, or prepare the target table separately.");
		return name;
	}

	static Plan result(String fingerprint, List<Statement> statements, List<String> warnings,
			TransferJobConfiguration.SchemaMode mode) {
		if (mode == TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING && warnings.isEmpty())
			return new Plan(fingerprint, statements);
		final String canonical = new com.sqlapp.util.JsonConverter()
				.toJsonString(List.of(mode.name(), fingerprint, statements, warnings));
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

	static void compatibility(TransferJobConfiguration.CompatibilityPolicy policy, List<String> warnings,
			String message) {
		if (policy == TransferJobConfiguration.CompatibilityPolicy.ERROR)
			throw new ValidationException(message);
		warnings.add(message);
	}

	static void mappingExpressions(TransferJobConfiguration.CompatibilityPolicy policy, List<String> warnings) {
		compatibility(policy, warnings, TransferSchemaChangeChecks.MAPPING_EXPRESSIONS
				+ " Table/column names are mapped; SQL expressions, checks, defaults and predicates remain verbatim. Review Schema SQL and adjust source expressions where necessary.");
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
