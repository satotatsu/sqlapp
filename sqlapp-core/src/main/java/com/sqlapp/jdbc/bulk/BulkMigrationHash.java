/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
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
		return rows(rows, columns, columns);
	}

	static String rows(final List<Row> rows, final List<Column> valueColumns,
			final List<Column> canonicalColumns) {
		if (valueColumns.size() != canonicalColumns.size()) {
			throw new IllegalArgumentException("Value and canonical column counts must match");
		}
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (final Row row : rows) {
				for (int i = 0; i < valueColumns.size(); i++) {
					final Object value = row.get(valueColumns.get(i));
					value(digest, canonicalColumns.get(i).getConverter().convertObject(value));
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
		if (value instanceof ByteBuffer buffer) {
			final ByteBuffer copy = buffer.asReadOnlyBuffer();
			final byte[] bytes = new byte[copy.remaining()];
			copy.get(bytes);
			value(digest, bytes);
			return;
		}
		if (value instanceof Number number) {
			final String text = canonicalNumber(number);
			bytes(digest, "number:" + text);
			return;
		}
		bytes(digest, value.getClass().getName() + ':' + value);
	}

	private static String canonicalNumber(final Number value) {
		if (value instanceof Double number && !Double.isFinite(number)) {
			return number.toString();
		}
		if (value instanceof Float number && !Float.isFinite(number)) {
			return number.toString();
		}
		final BigDecimal decimal;
		if (value instanceof BigDecimal number) {
			decimal = number;
		} else if (value instanceof BigInteger number) {
			decimal = new BigDecimal(number);
		} else if (value instanceof Byte || value instanceof Short
				|| value instanceof Integer || value instanceof Long) {
			decimal = BigDecimal.valueOf(value.longValue());
		} else {
			decimal = BigDecimal.valueOf(value.doubleValue());
		}
		return decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString();
	}

	private static void bytes(final MessageDigest digest, final String value) {
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
