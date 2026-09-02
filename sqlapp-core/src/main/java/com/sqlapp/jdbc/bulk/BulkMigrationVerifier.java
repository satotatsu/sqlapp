/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.function.SQLFunction;

/** Compares ordered row streams by total count and deterministic chunk hashes. */
public final class BulkMigrationVerifier {
	private BulkMigrationVerifier() {
	}

	public static BulkMigrationVerificationResult verify(final Table expected,
			final Table actual, final int chunkSize) {
		return verify(expected, expected.getRows().iterator(), actual,
				actual.getRows().iterator(), chunkSize);
	}

	/** Compares caller-supplied ordered streams using the two table definitions. */
	public static BulkMigrationVerificationResult verify(final Table expected,
			final Iterator<Row> expectedRows, final Table actual,
			final Iterator<Row> actualRows, final int chunkSize) {
		return verify(expected, expectedRows, actual, actualRows,
				expected.getColumns().stream().map(Column::getName).toList(), chunkSize);
	}

	/** Compares only the named columns, in the supplied canonical order. */
	public static BulkMigrationVerificationResult verify(final Table expected,
			final Iterator<Row> expectedRows, final Table actual,
			final Iterator<Row> actualRows, final List<String> columnNames,
			final int chunkSize) {
		try {
			return verify(expected, expectedRows, actual, actualRows, columnNames, chunkSize,
					null, null);
		} catch (SQLException e) {
			throw new IllegalStateException("Unexpected key encoding failure", e);
		}
	}

	/** Compares named columns and records optional source/target key boundaries. */
	public static BulkMigrationVerificationResult verify(final Table expected,
			final Iterator<Row> expectedRows, final Table actual,
			final Iterator<Row> actualRows, final List<String> columnNames,
			final int chunkSize, final SQLFunction<Row, String> expectedKey,
			final SQLFunction<Row, String> actualKey) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(actual, "actual");
		Objects.requireNonNull(expectedRows, "expectedRows");
		Objects.requireNonNull(actualRows, "actualRows");
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		Objects.requireNonNull(columnNames, "columnNames");
		if ((expectedKey == null) != (actualKey == null)) {
			throw new IllegalArgumentException("Both key encoders must be supplied together");
		}
		if (columnNames.isEmpty()) {
			throw new IllegalArgumentException("At least one verification column is required");
		}
		final java.util.Set<String> unique = new java.util.HashSet<>();
		final List<Column> expectedColumns = columnNames.stream().map(name -> {
			final Column column = expected.getColumns().get(name);
			if (column == null) {
				throw new IllegalArgumentException("Expected table is missing verification column: "
						+ name);
			}
			if (!unique.add(column.getName())) {
				throw new IllegalArgumentException("Duplicate verification column: " + name);
			}
			return column;
		}).toList();
		final List<Column> actualColumns = expectedColumns.stream().map(column -> {
			final Column match = actual.getColumns().get(column.getName());
			if (match == null) {
				throw new IllegalArgumentException("Actual table is missing column: " + column.getName());
			}
			return match;
		}).toList();
		final List<BulkMigrationVerificationChunk> chunks = new ArrayList<>();
		long expectedCount = 0;
		long actualCount = 0;
		long index = 0;
		Throwable failure = null;
		try {
			while (expectedRows.hasNext() || actualRows.hasNext()) {
				final List<Row> left = take(expectedRows, chunkSize);
				final List<Row> right = take(actualRows, chunkSize);
				expectedCount += left.size();
				actualCount += right.size();
				chunks.add(new BulkMigrationVerificationChunk(index++, left.size(), right.size(),
						BulkMigrationHash.rows(left, expectedColumns),
						BulkMigrationHash.rows(right, actualColumns, expectedColumns),
						firstKey(left, expectedKey), lastKey(left, expectedKey),
						firstKey(right, actualKey), lastKey(right, actualKey)));
			}
			return new BulkMigrationVerificationResult(chunkSize, expectedCount, actualCount,
					expectedColumns.stream().map(Column::getName).toList(),
					List.copyOf(chunks));
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, expectedRows, actualRows);
		}
	}

	/** Compares two keyset sources and retains their token configuration fingerprints. */
	public static BulkMigrationVerificationResult verify(
			final BulkMigrationKeysetSource expected,
			final BulkMigrationKeysetSource actual, final List<String> columnNames,
			final int chunkSize) throws SQLException {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(actual, "actual");
		final String expectedFingerprint = requireFingerprint(expected,
				"expected");
		final String actualFingerprint = requireFingerprint(actual, "actual");
		Iterator<Row> expectedRows = null;
		Iterator<Row> actualRows = null;
		boolean delegated = false;
		Throwable failure = null;
		try {
			expectedRows = expected.iterator(null);
			actualRows = actual.iterator(null);
			delegated = true;
			final var result = verify(expected.getTable(), expectedRows,
					actual.getTable(), actualRows, columnNames, chunkSize,
					expected::resumeToken, actual::resumeToken);
			return new BulkMigrationVerificationResult(result.getChunkSize(),
					result.getExpectedRows(), result.getActualRows(), result.getColumns(),
					expectedFingerprint, actualFingerprint, result.getChunks());
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			if (!delegated) {
				BulkMigrationIteratorSupport.close(failure, expectedRows, actualRows);
			}
		}
	}

	private static String requireFingerprint(final BulkMigrationKeysetSource source,
			final String side) {
		final String fingerprint = source.getConfigurationFingerprint();
		if (fingerprint == null || fingerprint.isBlank()) {
			throw new IllegalArgumentException(side
					+ " keyset source configuration fingerprint must not be empty");
		}
		return fingerprint;
	}

	private static String firstKey(final List<Row> rows,
			final SQLFunction<Row, String> key) throws SQLException {
		return key == null || rows.isEmpty() ? null : key.apply(rows.get(0));
	}

	private static String lastKey(final List<Row> rows,
			final SQLFunction<Row, String> key) throws SQLException {
		return key == null || rows.isEmpty() ? null : key.apply(rows.get(rows.size() - 1));
	}

	private static List<Row> take(final Iterator<Row> iterator, final int size) {
		final List<Row> rows = new ArrayList<>(size);
		while (rows.size() < size && iterator.hasNext()) {
			rows.add(iterator.next());
		}
		return rows;
	}

}
