/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

import lombok.Getter;

/** Reviewable repair dry-run snapshot that can be validated immediately before execution. */
@Getter
public final class BulkMigrationRepairPlan {
	private final Table expected;
	private final BulkMigrationKeysetSource expectedKeysetSource;
	private final Table target;
	private final BulkMigrationVerificationResult verification;
	private final BulkMigrationRepairOption options;
	private final List<BulkMigrationVerificationChunk> mismatchChunks;
	private final long estimatedReplayRows;
	private final List<String> keyColumns;
	private final List<String> stagingColumns;
	private final List<String> updateColumns;
	private final String stagingTableName;
	private final boolean transactionBreakingStaging;
	private final boolean atomic;
	private final String databaseProductName;
	private final String databaseProductVersion;
	private final String executorClassName;
	private final String fingerprint;

	BulkMigrationRepairPlan(final Table expected,
			final BulkMigrationKeysetSource expectedKeysetSource, final Table target,
			final BulkMigrationVerificationResult verification,
			final BulkMigrationRepairOption options, final BulkUpsertPlan upsertPlan,
			final boolean transactionBreakingStaging, final boolean atomic,
			final String databaseProductName,
			final String databaseProductVersion, final String executorClassName) {
		this.expected = Objects.requireNonNull(expected, "expected");
		this.expectedKeysetSource = expectedKeysetSource;
		this.target = Objects.requireNonNull(target, "target");
		this.verification = Objects.requireNonNull(verification, "verification");
		this.options = Objects.requireNonNull(options, "options");
		this.mismatchChunks = List.copyOf(verification.getMismatches());
		this.estimatedReplayRows = mismatchChunks.stream()
				.mapToLong(BulkMigrationVerificationChunk::getExpectedRows).sum();
		this.keyColumns = names(upsertPlan == null ? List.of() : upsertPlan.getKeyColumns());
		this.stagingColumns = names(upsertPlan == null ? List.of() : upsertPlan.getStagingColumns());
		this.updateColumns = names(upsertPlan == null ? List.of() : upsertPlan.getUpdateColumns());
		final BulkUpsertOption upsert = effectiveUpsertOption(options);
		this.stagingTableName = upsert.getStagingTableName();
		this.transactionBreakingStaging = transactionBreakingStaging;
		this.atomic = atomic;
		this.databaseProductName = Objects.requireNonNull(databaseProductName,
				"databaseProductName");
		this.databaseProductVersion = Objects.requireNonNull(databaseProductVersion,
				"databaseProductVersion");
		this.executorClassName = Objects.requireNonNull(executorClassName,
				"executorClassName");
		this.fingerprint = calculateFingerprint();
	}

	public boolean isKeysetSource() {
		return expectedKeysetSource != null;
	}

	public boolean isNoOp() {
		return mismatchChunks.isEmpty();
	}

	/** True when configuration and referenced Schema models still match this plan. */
	public boolean isUnchanged() {
		return fingerprint.equals(calculateFingerprint());
	}

	public void validateUnchanged() {
		if (!isUnchanged()) {
			throw new IllegalStateException("Migration repair plan changed after it was created");
		}
	}

	private String calculateFingerprint() {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, databaseProductName, databaseProductVersion, executorClassName,
					transactionBreakingStaging, atomic, expectedKeysetSource != null,
					expectedKeysetSource == null ? null
							: expectedKeysetSource.getConfigurationFingerprint());
			table(digest, expected);
			table(digest, target);
			update(digest, verification.getChunkSize(), verification.getExpectedRows(),
					verification.getActualRows(), verification.getExpectedKeysetFingerprint(),
					verification.getActualKeysetFingerprint());
			list(digest, verification.getColumns());
			for (final BulkMigrationVerificationChunk chunk : verification.getChunks()) {
				update(digest, chunk.getIndex(), chunk.getExpectedRows(), chunk.getActualRows(),
						chunk.getExpectedHash(), chunk.getActualHash(),
						chunk.getExpectedFirstKey(), chunk.getExpectedLastKey(),
						chunk.getActualFirstKey(), chunk.getActualLastKey());
			}
			final BulkUpsertOption upsert = effectiveUpsertOption(options);
			update(digest, options.isVerifyExpectedHashes(), options.getMaxBufferedRows(),
					upsert.isUseTransaction(), upsert.isUpdateWhenMatched(),
					upsert.isInsertWhenNotMatched(), upsert.getDuplicateKeyStrategy(),
					upsert.getDuplicateRowSelectorFingerprint(), upsert.getStagingTableName());
			list(digest, upsert.getKeyColumns());
			list(digest, upsert.getUpdateColumns());
			bulk(digest, upsert.getBulkOption());
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static BulkUpsertOption effectiveUpsertOption(
			final BulkMigrationRepairOption options) {
		return options.getBulkUpsertOption() == null ? BulkUpsertOption.defaults()
				: options.getBulkUpsertOption();
	}

	private static List<String> names(final List<Column> columns) {
		return columns.stream().map(Column::getName).toList();
	}

	private static void table(final MessageDigest digest, final Table table) {
		update(digest, table.getCatalogName(), table.getSchemaName(), table.getName(),
				table.getColumns().size());
		for (final Column column : table.getColumns()) {
			update(digest, column.getName(), column.getDataType(), column.getDataTypeName(),
					column.getLength(), column.getScale(), column.isNotNull(),
					column.isIdentity(), column.isHidden(), column.getFormula());
		}
		if (table.getPrimaryKeyConstraint() == null) {
			update(digest, (Object) null);
		} else {
			update(digest, table.getPrimaryKeyConstraint().getName());
			table.getPrimaryKeyConstraint().getColumns()
					.forEach(column -> update(digest, column.getName()));
		}
	}

	private static void list(final MessageDigest digest, final List<String> values) {
		update(digest, values.size());
		values.forEach(value -> update(digest, value));
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
