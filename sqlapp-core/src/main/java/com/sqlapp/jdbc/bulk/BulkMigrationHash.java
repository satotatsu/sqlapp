/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;

final class BulkMigrationHash {
	private BulkMigrationHash() {
	}

	static String rows(final List<Row> rows, final List<Column> columns) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (final Row row : rows) {
				for (final Column column : columns) {
					value(digest, row.get(column));
				}
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void value(final MessageDigest digest, final Object value) {
		if (value == null) {
			digest.update((byte) 0);
			return;
		}
		digest.update((byte) 1);
		if (value.getClass().isArray()) {
			final int length = Array.getLength(value);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(length).array());
			for (int i = 0; i < length; i++) {
				value(digest, Array.get(value, i));
			}
			return;
		}
		final byte[] bytes = (value.getClass().getName() + ':' + value)
				.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
