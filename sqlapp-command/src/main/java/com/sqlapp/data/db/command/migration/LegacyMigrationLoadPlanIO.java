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
import java.util.Objects;
import java.util.Set;

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
			AtomicMigrationFile.write(file.toPath(),
					temporary -> converter.writeJsonValue(temporary.toFile(), plan));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace RDB load plan: " + file, e);
		}
	}

	static LegacyMigrationLoadPlan validate(LegacyMigrationLoadPlan plan) {
		if (plan == null || !LegacyMigrationLoadPlan.FORMAT.equals(plan.getFormat())) {
			throw new CommandException("Unsupported legacy RDB load plan format.");
		}
		if (plan.getVersion() != LegacyMigrationLoadPlan.CURRENT_VERSION) {
			throw new CommandException("Unsupported legacy RDB load plan version: "
					+ plan.getVersion());
		}
		nonBlank(plan.getMigrationId(), "migrationId");
		nonBlank(plan.getContractFile(), "contractFile");
		nonBlank(plan.getContractFingerprint(), "contractFingerprint");
		nonBlank(plan.getSchemaFile(), "schemaFile");
		nonBlank(plan.getSchemaFingerprint(), "schemaFingerprint");
		if (!Set.of("INSERT", "INSERT_IGNORE", "MERGE", "REPLACE")
				.contains(plan.getTableOperationMode())) {
			throw new CommandException("Unsupported table operation mode: "
					+ plan.getTableOperationMode());
		}
		if (!Set.of("DIALECT", "HOLD", "REOPEN").contains(plan.getRootCursorStrategy())) {
			throw new CommandException("Unsupported root cursor strategy: "
					+ plan.getRootCursorStrategy());
		}
		if (plan.getRootBatchSize() <= 0 || plan.getCommitEveryRootBatches() <= 0) {
			throw new CommandException(
					"rootBatchSize and commitEveryRootBatches must be greater than zero.");
		}
		if (plan.getTransaction() == null || plan.getTransaction().isAutoCommit()
				|| !"ROOT_BATCH".equals(plan.getTransaction().getCommitUnit())
				|| !"BEFORE_COMMIT".equals(plan.getTransaction().getStagingDeleteTiming())
				|| !plan.getTransaction().isTargetAndStagingDeleteAtomic()
				|| !"ROOT".equals(plan.getTransaction().getRestartUnit())) {
			throw new CommandException("Legacy RDB load plan transaction policy is unsupported.");
		}
		if (plan.getDataSets() == null || plan.getDataSets().isEmpty()) {
			throw new CommandException("Legacy RDB load plan contains no data sets.");
		}
		final var byId = new HashMap<String, LegacyMigrationLoadPlan.LoadDataSet>();
		for (var dataSet : plan.getDataSets()) {
			if (dataSet == null) {
				throw new CommandException("Legacy RDB load plan contains a null data set.");
			}
			nonBlank(dataSet.getId(), "dataSet.id");
			nonBlank(dataSet.getFileName(), "dataSet.fileName");
			nonBlank(dataSet.getStagingTable(), "dataSet.stagingTable");
			nonBlank(dataSet.getTargetTable(), "dataSet.targetTable");
			if (byId.putIfAbsent(dataSet.getId(), dataSet) != null) {
				throw new CommandException("Duplicate load data set id: " + dataSet.getId());
			}
			validateFields(dataSet);
		}
		for (var dataSet : plan.getDataSets()) {
			if (dataSet.getParentDataSetId() == null) {
				if (dataSet.getSourceBusinessKey() == null
						|| dataSet.getSourceBusinessKey().isEmpty()) {
					throw new CommandException("Root data set requires sourceBusinessKey: "
							+ dataSet.getId());
				}
			} else if (!byId.containsKey(dataSet.getParentDataSetId())
					|| dataSet.getParentJoinKeys() == null
					|| dataSet.getParentJoinKeys().isEmpty()) {
				throw new CommandException("Child data set parent or join keys are invalid: "
						+ dataSet.getId());
			}
			if (dataSet.getParentDataSetId() != null) {
				var parent = byId.get(dataSet.getParentDataSetId());
				if (dataSet.getHierarchyDepth() <= parent.getHierarchyDepth()
						|| dataSet.getLoadOrder() <= parent.getLoadOrder()) {
					throw new CommandException("Child data set hierarchy is inconsistent: "
							+ dataSet.getId());
				}
				validateJoinKeys(dataSet);
			}
			final var visited = new HashSet<String>();
			var current = dataSet;
			while (current.getParentDataSetId() != null) {
				if (!visited.add(current.getId())) {
					throw new CommandException("Cyclic load data set hierarchy at: "
							+ current.getId());
				}
				current = byId.get(current.getParentDataSetId());
			}
		}
		return plan;
	}

	private static void validateFields(LegacyMigrationLoadPlan.LoadDataSet dataSet) {
		if (dataSet.getFields() == null || dataSet.getFields().isEmpty()) {
			throw new CommandException("Load data set contains no fields: " + dataSet.getId());
		}
		final var stagingColumns = new HashSet<String>();
		final var csvPositions = new HashSet<Integer>();
		int extractedFields = 0;
		for (var field : dataSet.getFields()) {
			if (field == null
					|| blank(field.getTargetColumn()) && !"DROP".equals(field.getAction())) {
				throw new CommandException("Load data set contains an invalid field: "
						+ dataSet.getId());
			}
			if (field.isExtracted()) {
				extractedFields++;
				if (field.getCsvPosition() <= 0 || blank(field.getStagingColumn())
						|| !csvPositions.add(field.getCsvPosition())
						|| !stagingColumns.add(field.getStagingColumn().toLowerCase(java.util.Locale.ROOT))) {
					throw new CommandException("Extracted load fields require unique positions and columns: "
							+ dataSet.getId());
				}
			} else if (field.getCsvPosition() != 0) {
				throw new CommandException("Generated load field csvPosition must be zero: "
						+ dataSet.getId());
			}
		}
		if (extractedFields == 0 || csvPositions.size() != extractedFields
				|| !csvPositions.containsAll(java.util.stream.IntStream
						.rangeClosed(1, extractedFields).boxed().toList())) {
			throw new CommandException("Extracted load field positions must be contiguous: "
					+ dataSet.getId());
		}
		validateNames(dataSet.getSourceBusinessKey(), "sourceBusinessKey", dataSet.getId());
		validateNames(dataSet.getTargetPrimaryKey(), "targetPrimaryKey", dataSet.getId());
	}

	private static void validateJoinKeys(LegacyMigrationLoadPlan.LoadDataSet dataSet) {
		final var childColumns = new HashSet<String>();
		for (var key : dataSet.getParentJoinKeys()) {
			if (key == null || blank(key.getParentStagingColumn())
					|| blank(key.getChildStagingColumn())
					|| blank(key.getTargetForeignKeyColumn())
					|| !childColumns.add(key.getChildStagingColumn()
							.toLowerCase(java.util.Locale.ROOT))) {
				throw new CommandException("Child data set join keys are invalid: "
						+ dataSet.getId());
			}
		}
	}

	private static void validateNames(java.util.List<String> values, String role,
			String dataSetId) {
		if (values == null || values.stream().anyMatch(LegacyMigrationLoadPlanIO::blank)
				|| new HashSet<>(values).size() != values.size()) {
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
