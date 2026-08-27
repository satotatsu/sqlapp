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
	private final String fingerprint;

	public BulkMigrationJobPlan(final List<BulkMigrationJobTask> tasks) {
		this.tasks = List.copyOf(tasks);
		this.fingerprint = fingerprint(this.tasks);
	}

	public List<String> getTaskIds() {
		return tasks.stream().map(BulkMigrationJobTask::getTaskId).toList();
	}

	public boolean isUnchanged() {
		return fingerprint.equals(fingerprint(tasks));
	}

	public void validateUnchanged() {
		if (!isUnchanged()) {
			throw new IllegalStateException("Migration job plan changed after it was created");
		}
	}

	private static String fingerprint(final List<BulkMigrationJobTask> tasks) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
				if (option.getMode() == BulkMigrationMode.INSERT) {
					bulk(digest, option.getBulkOption());
				} else {
					final BulkUpsertOption upsert = option.getBulkUpsertOption() == null
							? BulkUpsertOption.defaults() : option.getBulkUpsertOption();
					list(digest, upsert.getKeyColumns());
					list(digest, upsert.getUpdateColumns());
					update(digest, upsert.isUpdateWhenMatched(), upsert.isInsertWhenNotMatched(),
							upsert.isUseTransaction(), upsert.getDuplicateKeyStrategy(),
							upsert.getDuplicateRowSelector() != null, upsert.getStagingTableName());
					bulk(digest, upsert.getBulkOption());
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
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
