/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.snapshot;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationSnapshotDefinition;

/**
 * Deterministic identity of a resolved SCD2 definition and its table shapes.
 */
public final class MigrationSnapshotConfigurationFingerprint {
	private MigrationSnapshotConfigurationFingerprint() {
	}

	public static String calculate(final Table source, final Table target, final MigrationSnapshotDefinition definition,
			final Instant effectiveAt, final int fetchSize, final int batchSize, final Duration approvalValidFor) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, "sqlapp-migration-snapshot", 1, definition.id(), definition.tableId(), effectiveAt,
					fetchSize, batchSize, approvalValidFor, definition.expireMissingRows(),
					definition.validFromColumn(), definition.validToColumn(), definition.currentColumn());
			list(digest, definition.keyColumns());
			list(digest, definition.trackedColumns());
			table(digest, source);
			table(digest, target);
			return "sha256:" + HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private static void table(final MessageDigest digest, final Table table) {
		update(digest, table.getCatalogName(), table.getSchemaName(), table.getName(), table.getColumns().size());
		table.getColumns()
				.forEach(column -> update(digest, column.getName(), column.getDataType(), column.getDataTypeName(),
						column.getLength(), column.getScale(), column.isNotNull(), column.isIdentity(),
						column.isHidden(), column.getFormula(), column.getDefaultValue()));
	}

	private static void list(final MessageDigest digest, final List<String> values) {
		update(digest, values.size());
		values.forEach(value -> update(digest, value));
	}

	private static void update(final MessageDigest digest, final Object... values) {
		for (final Object value : values) {
			if (value == null) {
				digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
			} else {
				final byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
				digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
				digest.update(bytes);
			}
		}
	}
}
