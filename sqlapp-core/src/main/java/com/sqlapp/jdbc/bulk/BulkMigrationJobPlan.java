/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.sqlapp.data.schemas.Table;

import lombok.Getter;

/** Validated migration tasks in their final dependency execution order. */
@Getter
public class BulkMigrationJobPlan {
	private final List<BulkMigrationJobTask> tasks;
	private final BulkMigrationJobLifecycle lifecycle;
	private final List<BulkMigrationJobOperation> operations;
	private final String fingerprint;

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks) {
		this(tasks, BulkMigrationJobLifecycle.NO_OP);
	}

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle) {
		this.tasks = List.copyOf(tasks);
		this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
		this.operations = List.copyOf(lifecycle.plan(this.tasks));
		this.fingerprint = fingerprint(this.tasks, lifecycle, operations);
	}

	public List<String> getTaskIds() {
		return tasks.stream().map(BulkMigrationJobTask::getTaskId).toList();
	}

	public boolean isUnchanged() {
		return fingerprint.equals(fingerprint(tasks, lifecycle,
				List.copyOf(lifecycle.plan(tasks))));
	}

	public void validateUnchanged() {
		if (!isUnchanged()) {
			throw new IllegalStateException("Migration job plan changed after it was created");
		}
	}

	private static String fingerprint(final List<BulkMigrationJobTask> tasks,
			final BulkMigrationJobLifecycle lifecycle,
			final List<BulkMigrationJobOperation> operations) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, lifecycle.getConfigurationFingerprint(), operations.size());
			operations.forEach(operation -> update(digest, operation.id(), operation.phase(),
					operation.description(), operation.transactionBreaking()));
			for (final BulkMigrationJobTask task : tasks) {
				final Table table = task.getSourceTable() != null ? task.getSourceTable()
						: task.getKeysetSource().getTable();
				final ChunkedBulkMigrationOption option = task.getOptions();
				update(digest, task.getTaskId(), table.getCatalogName(), table.getSchemaName(),
						table.getName(), task.getKeysetSource() != null,
						option.getMigrationId(), option.getChunkSize(),
						option.getMode(), option.isResume(), option.getCheckpointMode(),
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
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
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
			update(digest, foreignKey.getName(), foreignKey.getRelatedTableSchemaName(),
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
