/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and writes legacy RDB load plans.
 */
public class LegacyMigrationLoadPlanIO {
	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationLoadPlan read(File file) {
		Objects.requireNonNull(file, "file");
		if (!file.isFile()) {
			throw new CommandException("Legacy RDB load plan does not exist: " + file);
		}
		try {
			return validate(converter.fromJsonString(file, LegacyMigrationLoadPlan.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read legacy RDB load plan: " + file, e);
		}
	}

	public void write(File file, LegacyMigrationLoadPlan plan) {
		Objects.requireNonNull(file, "file");
		validate(plan);
		try {
			AtomicMigrationFile.write(file.toPath(), temporary -> converter.writeJsonValue(temporary.toFile(), plan));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace RDB load plan: " + file, e);
		}
	}

	static LegacyMigrationLoadPlan validate(LegacyMigrationLoadPlan plan) {
		validateExecution(plan);
		nonBlank(plan.getMigrationId(), "migrationId");
		nonBlank(plan.getContractFile(), "contractFile");
		nonBlank(plan.getContractFingerprint(), "contractFingerprint");
		nonBlank(plan.getSchemaFile(), "schemaFile");
		nonBlank(plan.getSchemaFingerprint(), "schemaFingerprint");
		validateArtifactDataSets(plan);
		validateViewpointMetadata(plan);
		return plan;
	}

	static LegacyMigrationLoadPlan validateExecution(LegacyMigrationLoadPlan plan) {
		if (plan == null || !LegacyMigrationLoadPlan.FORMAT.equals(plan.getFormat())) {
			throw new CommandException("Unsupported legacy RDB load plan format.");
		}
		if (plan.getVersion() != LegacyMigrationLoadPlan.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy RDB load plan version: " + plan.getVersion());
		}
		if (plan.getTableOperationMode() == null) {
			throw new CommandException("Unsupported table operation mode: " + plan.getTableOperationMode());
		}
		if (plan.getRootCursorStrategy() == null) {
			throw new CommandException("Unsupported root cursor strategy: " + plan.getRootCursorStrategy());
		}
		if (plan.getRootBatchSize() <= 0 || plan.getCommitEveryRootBatches() <= 0) {
			throw new CommandException("rootBatchSize and commitEveryRootBatches must be greater than zero.");
		}
		if (plan.getTransaction() == null || plan.getTransaction().isAutoCommit()
				|| plan.getTransaction().getCommitUnit() != LegacyMigrationLoadPlan.CommitUnit.ROOT_BATCH
				|| plan.getTransaction()
						.getStagingDeleteTiming() != LegacyMigrationLoadPlan.StagingDeleteTiming.BEFORE_COMMIT
				|| !plan.getTransaction().isTargetAndStagingDeleteAtomic()
				|| plan.getTransaction().getRestartUnit() != LegacyMigrationLoadPlan.RestartUnit.ROOT) {
			throw new CommandException("Legacy RDB load plan transaction policy is unsupported.");
		}
		if (plan.getDataSets() == null || plan.getDataSets().isEmpty()) {
			throw new CommandException("Legacy RDB load plan contains no data sets.");
		}
		final var byId = new HashMap<String, LegacyMigrationLoadPlan.LoadDataSet>();
		final var stagingTables = new HashSet<String>();
		for (var dataSet : plan.getDataSets()) {
			if (dataSet == null) {
				throw new CommandException("Legacy RDB load plan contains a null data set.");
			}
			nonBlank(dataSet.getId(), "dataSet.id");
			nonBlank(dataSet.getStagingTable(), "dataSet.stagingTable");
			nonBlank(dataSet.getTargetTable(), "dataSet.targetTable");
			if (!stagingTables.add(dataSet.getStagingTable().toLowerCase(java.util.Locale.ROOT))) {
				throw new CommandException("Duplicate staging table: " + dataSet.getStagingTable());
			}
			if (dataSet.getHierarchyDepth() < 0 || dataSet.getLoadOrder() < 0) {
				throw new CommandException("Load data set hierarchy or load order is invalid: " + dataSet.getId());
			}
			if (byId.putIfAbsent(dataSet.getId(), dataSet) != null) {
				throw new CommandException("Duplicate load data set id: " + dataSet.getId());
			}
			validateFields(dataSet);
		}
		List<String> orderedIds = plan.getDataSets().stream()
				.sorted(Comparator.comparingInt(LegacyMigrationLoadPlan.LoadDataSet::getLoadOrder)
						.thenComparing(LegacyMigrationLoadPlan.LoadDataSet::getId))
				.map(LegacyMigrationLoadPlan.LoadDataSet::getId).toList();
		if (!orderedIds.equals(plan.getDataSets().stream().map(LegacyMigrationLoadPlan.LoadDataSet::getId).toList())) {
			throw new CommandException("Load data sets must be ordered by loadOrder and id.");
		}
		for (var dataSet : plan.getDataSets()) {
			if (dataSet.getParentDataSetId() == null) {
				if (dataSet.getSourceBusinessKey() == null || dataSet.getSourceBusinessKey().isEmpty()) {
					throw new CommandException("Root data set requires sourceBusinessKey: " + dataSet.getId());
				}
				if (dataSet.getTargetForeignKey() == null || !dataSet.getTargetForeignKey().isEmpty()
						|| dataSet.getParentJoinKeys() == null || !dataSet.getParentJoinKeys().isEmpty()) {
					throw new CommandException(
							"Root data set must not define targetForeignKey or parentJoinKeys: " + dataSet.getId());
				}
			} else if (!byId.containsKey(dataSet.getParentDataSetId()) || dataSet.getParentJoinKeys() == null
					|| dataSet.getParentJoinKeys().isEmpty() || dataSet.getTargetForeignKey() == null
					|| dataSet.getTargetForeignKey().isEmpty()) {
				throw new CommandException("Child data set parent or join keys are invalid: " + dataSet.getId());
			}
			if (dataSet.getParentDataSetId() != null) {
				var parent = byId.get(dataSet.getParentDataSetId());
				if (dataSet.getHierarchyDepth() <= parent.getHierarchyDepth()
						|| dataSet.getLoadOrder() <= parent.getLoadOrder()) {
					throw new CommandException("Child data set hierarchy is inconsistent: " + dataSet.getId());
				}
				validateJoinKeys(dataSet);
			} else if (dataSet.getHierarchyDepth() != 0) {
				throw new CommandException("Root load data set hierarchy depth must be zero: " + dataSet.getId());
			}
			final var visited = new HashSet<String>();
			var current = dataSet;
			while (current.getParentDataSetId() != null) {
				if (!visited.add(current.getId())) {
					throw new CommandException("Cyclic load data set hierarchy at: " + current.getId());
				}
				current = byId.get(current.getParentDataSetId());
			}
		}
		return plan;
	}

	private static void validateArtifactDataSets(LegacyMigrationLoadPlan plan) {
		final var csvFiles = new HashSet<String>();
		for (var dataSet : plan.getDataSets()) {
			nonBlank(dataSet.getFileName(), "dataSet.fileName");
			if (!csvFiles.add(dataSet.getFileName().toLowerCase(java.util.Locale.ROOT))) {
				throw new CommandException("Duplicate load CSV file name: " + dataSet.getFileName());
			}
		}
	}

	private static void validateViewpointMetadata(LegacyMigrationLoadPlan plan) {
		boolean selected = !blank(plan.getViewpointId());
		boolean hasMetadata = !blank(plan.getViewpointsFile()) || !blank(plan.getViewpointsFingerprint())
				|| hasValues(plan.getResolvedTableIds()) || hasValues(plan.getResolvedDataSetIds());
		if (!selected) {
			if (hasMetadata) {
				throw new CommandException("Legacy RDB load plan has viewpoint metadata but no viewpointId.");
			}
			return;
		}
		nonBlank(plan.getViewpointsFile(), "viewpointsFile");
		nonBlank(plan.getViewpointsFingerprint(), "viewpointsFingerprint");
		validateResolvedIds(plan.getResolvedTableIds(), "resolvedTableIds");
		validateResolvedIds(plan.getResolvedDataSetIds(), "resolvedDataSetIds");
		List<String> dataSetIds = plan.getDataSets().stream().map(LegacyMigrationLoadPlan.LoadDataSet::getId).toList();
		if (!dataSetIds.equals(plan.getResolvedDataSetIds())) {
			throw new CommandException("Legacy RDB load plan resolvedDataSetIds must match dataSets in order.");
		}
	}

	private static void validateResolvedIds(List<String> values, String role) {
		if (values == null || values.isEmpty() || values.stream().anyMatch(LegacyMigrationLoadPlanIO::blank)
				|| new HashSet<>(values).size() != values.size()) {
			throw new CommandException("Legacy RDB load plan " + role + " must contain unique non-empty values.");
		}
	}

	private static boolean hasValues(List<?> values) {
		return values != null && !values.isEmpty();
	}

	static void validateSchema(LegacyMigrationLoadPlan plan, List<Table> tables) {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(tables, "tables");
		var targetTables = new HashMap<String, Table>();
		for (var dataSet : plan.getDataSets()) {
			List<Table> matches = tables.stream()
					.filter(table -> matchesQualifier(dataSet.getTargetCatalog(), table.getCatalogName())
							&& equalsName(table.getSchemaName(), dataSet.getTargetSchema())
							&& equalsName(table.getName(), dataSet.getTargetTable()))
					.toList();
			if (matches.isEmpty()) {
				throw new CommandException("Target table was not found in schema: "
						+ qualified(dataSet.getTargetCatalog(), dataSet.getTargetSchema(), dataSet.getTargetTable()));
			}
			if (matches.size() > 1) {
				throw new CommandException("Target table is ambiguous in schema: "
						+ qualified(dataSet.getTargetCatalog(), dataSet.getTargetSchema(), dataSet.getTargetTable()));
			}
			Table table = matches.getFirst();
			targetTables.put(dataSet.getId(), table);
			for (var field : dataSet.getFields()) {
				if (field.getTargetColumn() != null && field
						.getAction() != com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction.DROP) {
					column(table, field.getTargetColumn(), "Target column", dataSet.getId());
				}
			}
			for (String key : dataSet.getTargetPrimaryKey()) {
				column(table, key, "Target primary-key column", dataSet.getId());
			}
			validateTargetKey(plan, dataSet, table);
		}
		for (var dataSet : plan.getDataSets()) {
			Set<String> staging = dataSet.getFields().stream().filter(LegacyMigrationLoadPlan.LoadField::isExtracted)
					.map(field -> field.getStagingColumn().toLowerCase(java.util.Locale.ROOT))
					.collect(java.util.stream.Collectors.toSet());
			for (String key : dataSet.getSourceBusinessKey()) {
				if (!staging.contains(key.toLowerCase(java.util.Locale.ROOT))) {
					throw new CommandException(
							"Source business-key staging column was not found: " + dataSet.getId() + "." + key);
				}
			}
			if (dataSet.getParentDataSetId() != null) {
				var parent = plan.getDataSets().stream()
						.filter(item -> item.getId().equals(dataSet.getParentDataSetId())).findFirst().orElseThrow();
				Table childTarget = targetTables.get(dataSet.getId());
				Table parentTarget = targetTables.get(parent.getId());
				List<String> targetForeignKey = dataSet.getTargetForeignKey();
				for (String key : targetForeignKey) {
					column(childTarget, key, "Target foreign-key column", dataSet.getId());
				}
				long matchingForeignKeys = childTarget.getConstraints().getForeignKeyConstraints().stream()
						.filter(foreignKey -> SchemaUtils.isSameTable(foreignKey.getRelatedTable(), parentTarget))
						.filter(foreignKey -> sameNames(targetForeignKey,
								foreignKey.getColumns().stream().map(Column::getName).toList()))
						.count();
				if (matchingForeignKeys != 1) {
					throw new CommandException(
							"Target parent foreign key does not uniquely match schema: " + dataSet.getId());
				}
				Set<String> parentStaging = parent.getFields().stream()
						.filter(LegacyMigrationLoadPlan.LoadField::isExtracted)
						.map(field -> field.getStagingColumn().toLowerCase(java.util.Locale.ROOT))
						.collect(java.util.stream.Collectors.toSet());
				for (var key : dataSet.getParentJoinKeys()) {
					if (!parentStaging.contains(key.getParentStagingColumn().toLowerCase(java.util.Locale.ROOT))
							|| !staging.contains(key.getChildStagingColumn().toLowerCase(java.util.Locale.ROOT))) {
						throw new CommandException(
								"Parent/child staging join column was not found: " + dataSet.getId());
					}
				}
			}
		}
	}

	private static void validateTargetKey(LegacyMigrationLoadPlan plan, LegacyMigrationLoadPlan.LoadDataSet dataSet,
			Table table) {
		if (!dataSet.getTargetPrimaryKey().isEmpty()) {
			var primaryKey = table.getPrimaryKeyConstraint();
			List<String> actual = primaryKey == null ? List.of()
					: primaryKey.getColumns().stream().map(column -> column.getName()).toList();
			if (!sameNames(dataSet.getTargetPrimaryKey(), actual)) {
				throw new CommandException("Target primary key disagrees with schema: " + dataSet.getId());
			}
		}
		if (plan.getTableOperationMode() != LegacyMigrationLoadPlan.TableOperationMode.INSERT) {
			SqlSignature signature = new SqlSignature(table, List.of());
			if (!signature.hasPrimaryKey() && !signature.hasUniqueKey() && !signature.hasNotNullUniqueIndex()) {
				throw new CommandException("Target table requires a primary key, unique key, or "
						+ "non-null unique index for " + plan.getTableOperationMode() + ": " + dataSet.getId());
			}
		}
	}

	private static boolean sameNames(List<String> left, List<String> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			if (!equalsName(left.get(i), right.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static void column(Table table, String name, String role, String dataSetId) {
		long matches = table.getColumns().stream().filter(item -> equalsName(item.getName(), name)).count();
		if (matches == 0) {
			throw new CommandException(role + " was not found: " + dataSetId + "." + name);
		}
		if (matches > 1) {
			throw new CommandException(role + " is ambiguous: " + dataSetId + "." + name);
		}
	}

	private static boolean equalsName(String left, String right) {
		return left == null ? right == null : right != null && left.equalsIgnoreCase(right);
	}

	private static boolean matchesQualifier(String expected, String actual) {
		return blank(expected) || equalsName(expected, actual);
	}

	private static String qualified(String catalog, String schema, String table) {
		var names = new java.util.ArrayList<String>();
		if (catalog != null && !catalog.isBlank()) {
			names.add(catalog);
		}
		if (schema != null && !schema.isBlank()) {
			names.add(schema);
		}
		names.add(table);
		return String.join(".", names);
	}

	private static void validateFields(LegacyMigrationLoadPlan.LoadDataSet dataSet) {
		if (dataSet.getFields() == null || dataSet.getFields().isEmpty()) {
			throw new CommandException("Load data set contains no fields: " + dataSet.getId());
		}
		final var stagingColumns = new HashSet<String>();
		final var csvPositions = new HashSet<Integer>();
		int extractedFields = 0;
		for (var field : dataSet.getFields()) {
			if (field == null || blank(field.getTargetColumn()) && field
					.getAction() != com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction.DROP) {
				throw new CommandException("Load data set contains an invalid field: " + dataSet.getId());
			}
			if (field.getAction() == null) {
				throw new CommandException(
						"Load data set field action is unsupported: " + dataSet.getId() + "." + field.getAction());
			}
			if (!field.isExtracted() && !field.isTargetGenerated()) {
				throw new CommandException(
						"Load data set field must be extracted or target-generated: " + dataSet.getId());
			}
			if (field.isExtracted() && field.isTargetGenerated()) {
				throw new CommandException("Load data set field cannot be both extracted and target-generated: "
						+ dataSet.getId() + "." + field.getStagingColumn());
			}
			if (field.isTargetGenerated() && field
					.getAction() != com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction.GENERATE
					&& field.getAction() != com.sqlapp.data.schemas.migration.LegacyMigrationMapping.ColumnAction.CONSTANT) {
				throw new CommandException("Target-generated load field has an incompatible action: " + dataSet.getId()
						+ "." + field.getAction());
			}
			if (field.isExtracted()) {
				extractedFields++;
				if (field.getCsvPosition() <= 0 || blank(field.getStagingColumn())
						|| !csvPositions.add(field.getCsvPosition())
						|| !stagingColumns.add(field.getStagingColumn().toLowerCase(java.util.Locale.ROOT))) {
					throw new CommandException(
							"Extracted load fields require unique positions and columns: " + dataSet.getId());
				}
				if (isReservedStagingColumn(field.getStagingColumn())) {
					throw new CommandException("Extracted load field uses a reserved staging column: " + dataSet.getId()
							+ "." + field.getStagingColumn());
				}
			} else if (field.getCsvPosition() != 0) {
				throw new CommandException("Generated load field csvPosition must be zero: " + dataSet.getId());
			}
		}
		if (extractedFields == 0 || csvPositions.size() != extractedFields || !csvPositions
				.containsAll(java.util.stream.IntStream.rangeClosed(1, extractedFields).boxed().toList())) {
			throw new CommandException("Extracted load field positions must be contiguous: " + dataSet.getId());
		}
		validateNames(dataSet.getSourceBusinessKey(), "sourceBusinessKey", dataSet.getId());
		validateNames(dataSet.getTargetPrimaryKey(), "targetPrimaryKey", dataSet.getId());
		validateNames(dataSet.getTargetForeignKey(), "targetForeignKey", dataSet.getId());
	}

	private static boolean isReservedStagingColumn(String name) {
		return "SQLAPP_LOAD_STATUS".equalsIgnoreCase(name) || "SQLAPP_LOADED_AT".equalsIgnoreCase(name);
	}

	private static void validateJoinKeys(LegacyMigrationLoadPlan.LoadDataSet dataSet) {
		final var parentColumns = new HashSet<String>();
		final var childColumns = new HashSet<String>();
		for (var key : dataSet.getParentJoinKeys()) {
			if (key == null || blank(key.getParentStagingColumn()) || blank(key.getChildStagingColumn())
					|| !parentColumns.add(key.getParentStagingColumn().toLowerCase(java.util.Locale.ROOT))
					|| !childColumns.add(key.getChildStagingColumn().toLowerCase(java.util.Locale.ROOT))) {
				throw new CommandException("Child data set join keys are invalid: " + dataSet.getId());
			}
		}
	}

	private static void validateNames(java.util.List<String> values, String role, String dataSetId) {
		if (values == null || values.stream().anyMatch(LegacyMigrationLoadPlanIO::blank) || values.stream()
				.map(value -> value.toLowerCase(java.util.Locale.ROOT)).distinct().count() != values.size()) {
			throw new CommandException("Load data set " + role + " is invalid: " + dataSetId);
		}
	}

	private static void nonBlank(String value, String name) {
		if (blank(value)) {
			throw new CommandException("Legacy RDB load plan " + name + " must not be empty.");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
