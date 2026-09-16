/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.migration.MigrationNodeManifest;

import lombok.Getter;

/** Validated migration tasks in their final dependency execution order. */
@Getter
public class BulkMigrationJobPlan {
	private final List<BulkMigrationJobTask> tasks;
	private final BulkMigrationJobLifecycle lifecycle;
	private final List<BulkMigrationJobOperation> operations;
	private final String jobId;
	private final String fingerprint;

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks) {
		this(tasks, BulkMigrationJobLifecycle.NO_OP);
	}

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle) {
		this(tasks, lifecycle, null);
	}

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle, final String jobId) {
		this.tasks = List.copyOf(BulkMigrationJobExecutor.order(tasks));
		this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
		this.operations = List.copyOf(lifecycle.plan(this.tasks));
		this.jobId = jobId == null ? defaultJobId(this.tasks) : validateJobId(jobId);
		this.fingerprint = fingerprint(this.tasks, lifecycle, operations, this.jobId);
	}

	public List<String> getTaskIds() {
		return tasks.stream().map(BulkMigrationJobTask::getTaskId).toList();
	}

	/** Returns stable per-task fingerprints and explicit FK dependencies. */
	public MigrationNodeManifest getNodeManifest() {
		final Map<String, MigrationNodeManifest.Node> nodes = new LinkedHashMap<>();
		for (final BulkMigrationJobTask task : tasks) {
			final Table table = sourceTable(task);
			final List<String> dependencies = new ArrayList<>();
			table.getConstraints().getForeignKeyConstraints(fk -> fk.getRelatedTable() != null
					&& !SchemaUtils.isSameTable(fk.getRelatedTable(), table)).forEach(fk -> tasks.stream()
						.filter(candidate -> SchemaUtils.isSameTable(sourceTable(candidate), fk.getRelatedTable()))
						.map(BulkMigrationJobTask::getTaskId).filter(id -> !dependencies.contains(id))
						.forEach(dependencies::add));
			nodes.put(task.getTaskId(), new MigrationNodeManifest.Node(task.getTaskId(), taskFingerprint(task),
					dependencies));
		}
		return new MigrationNodeManifest(MigrationNodeManifest.CURRENT_VERSION, fingerprint, nodes);
	}

	/** Creates a dependency-ordered projection while preserving task objects. */
	public BulkMigrationJobPlan selectTasks(final Set<String> taskIds) {
		java.util.Objects.requireNonNull(taskIds, "taskIds");
		final List<String> unknown = taskIds.stream().filter(id -> tasks.stream()
				.noneMatch(task -> task.getTaskId().equals(id))).toList();
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("Unknown migration task IDs: " + unknown);
		}
		return new BulkMigrationJobPlan(tasks.stream().filter(task -> taskIds.contains(task.getTaskId())).toList(),
				lifecycle, jobId);
	}

	public boolean isUnchanged() {
		return fingerprint.equals(fingerprint(tasks, lifecycle,
				List.copyOf(lifecycle.plan(tasks)), jobId));
	}

	public void validateUnchanged() {
		if (!isUnchanged()) {
			throw new IllegalStateException("Migration job plan changed after it was created");
		}
	}

	private static String fingerprint(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle,
			final List<BulkMigrationJobOperation> operations, final String jobId) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, jobId,
					BulkMigrationJobLifecycle.requireConfigurationFingerprint(lifecycle),
					operations.size());
			operations.forEach(operation -> update(digest, operation.id(), operation.phase(),
					operation.description(), operation.transactionBreaking()));
			for (final BulkMigrationJobTask task : tasks) {
				task(digest, task);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String taskFingerprint(final BulkMigrationJobTask task) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			task(digest, task);
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void task(final MessageDigest digest, final BulkMigrationJobTask task) {
				final Table table = sourceTable(task);
				final ChunkedBulkMigrationOption option = task.getOptions();
				update(digest, task.getTaskId(), table.getCatalogName(), table.getSchemaName(),
						table.getName(), task.getKeysetSource() != null,
						option.getMigrationId(), option.getChunkSize(),
						option.getMode(), option.getIncrementalStrategy(), option.isResume(), option.getCheckpointMode(),
						option.getCheckpointTableName(), option.getSourceFingerprint(),
						option.getTargetFingerprint());
				table(digest, table);
				if (task.getKeysetSource() != null) {
					final String keysetFingerprint = task.getKeysetSource()
							.getConfigurationFingerprint();
					if (keysetFingerprint == null || keysetFingerprint.isBlank()) {
						throw new IllegalArgumentException("Keyset source configuration fingerprint "
								+ "must not be blank: " + task.getTaskId());
					}
					update(digest, keysetFingerprint);
				}
				if (option.getMode() == BulkMigrationMode.INSERT) {
					bulk(digest, option.getBulkOption());
				} else {
					final BulkUpsertOption upsert = option.getBulkUpsertOption() == null
							? BulkUpsertOption.defaults() : option.getBulkUpsertOption();
					BulkUpsertPlan.resolve(table, upsert);
					list(digest, upsert.getKeyColumns());
					list(digest, upsert.getUpdateColumns());
					update(digest, upsert.isUpdateWhenMatched(), upsert.isInsertWhenNotMatched(),
							upsert.isUseTransaction(), upsert.getDuplicateKeyStrategy(),
							upsert.getDuplicateRowSelectorFingerprint(), upsert.getStagingTableName());
					bulk(digest, upsert.getBulkOption());
				}
				final BulkMigrationRetryOption retry = option.getRetryOption();
				update(digest, retry.getMaxRetries(), retry.getInitialBackoffMillis(),
						retry.getBackoffMultiplier(), retry.getMaxBackoffMillis(),
						retry.isRetryTransientExceptions());
				list(digest, retry.getSqlStates());
				update(digest, retry.getErrorCodes().size());
				retry.getErrorCodes().forEach(code -> update(digest, code));
	}

	private static Table sourceTable(final BulkMigrationJobTask task) {
		return task.getSourceTable() != null ? task.getSourceTable() : task.getKeysetSource().getTable();
	}

	private static String defaultJobId(final List<BulkMigrationJobTask> tasks) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			tasks.stream().map(task -> {
				final Table table = task.getSourceTable() != null ? task.getSourceTable()
						: task.getKeysetSource().getTable();
				return List.of(value(task.getOptions().getMigrationId()),
						value(table.getCatalogName()), value(table.getSchemaName()),
						value(table.getName()));
			}).sorted((left, right) -> String.join("\u0000", left)
					.compareTo(String.join("\u0000", right)))
					.forEach(values -> update(digest, values.toArray()));
			return "job-" + HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String value(final String value) {
		return value == null ? "<null>" : value;
	}

	private static String validateJobId(final String jobId) {
		if (jobId.isBlank()) {
			throw new IllegalArgumentException("jobId must not be empty");
		}
		if (jobId.length() > 255) {
			throw new IllegalArgumentException("jobId must not exceed 255 characters");
		}
		return jobId;
	}

	private static void table(final MessageDigest digest, final Table table) {
		update(digest, table.getColumns().size());
		table.getColumns().forEach(column -> update(digest, column.getName(),
				column.getDataType(), column.getDataTypeName(), column.getLength(),
				column.getScale(), column.isNotNull(), column.isIdentity(), column.isHidden(),
				column.getFormula(), column.getDefaultValue()));
		if (table.getPrimaryKeyConstraint() == null) {
			update(digest, (Object) null);
		} else {
			update(digest, table.getPrimaryKeyConstraint().getName());
			update(digest, table.getPrimaryKeyConstraint().getColumns().size());
			table.getPrimaryKeyConstraint().getColumns()
					.forEach(column -> update(digest, column.getName()));
		}
		final var foreignKeys = table.getConstraints().getForeignKeyConstraints();
		update(digest, foreignKeys.size());
		foreignKeys.forEach(foreignKey -> {
			final Table relatedTable = foreignKey.getRelatedTable();
			update(digest, foreignKey.getName(),
					relatedTable == null ? null : relatedTable.getCatalogName(),
					foreignKey.getRelatedTableSchemaName(),
					foreignKey.getRelatedTableName(), foreignKey.getColumns().size(),
					foreignKey.getRelatedColumns().size());
			foreignKey.getColumns().forEach(column -> update(digest, column.getName()));
			foreignKey.getRelatedColumns()
					.forEach(column -> update(digest, column.getName()));
		});
	}

	private static void bulk(final MessageDigest digest, final BulkOption option) {
		if (option == null) {
			update(digest, (Object) null);
			return;
		}
		update(digest, option.getBatchSize(), option.getBulkCopyTimeout(),
				option.isCheckConstraints(), option.isFireTriggers(), option.isKeepIdentity(),
				option.isKeepNulls(), option.isTableLock(), option.isUseTransaction(),
				option.isAllowEncryptedValueModifications());
	}

	private static void list(final MessageDigest digest, final List<String> values) {
		if (values == null) {
			update(digest, (Object) null);
			return;
		}
		update(digest, values.size());
		for (final String value : values) {
			update(digest, value);
		}
	}

	private static void update(final MessageDigest digest, final Object... values) {
		for (final Object value : values) {
			if (value == null) {
				digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
				continue;
			}
			final byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
			digest.update(bytes);
		}
	}
}
