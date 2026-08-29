/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/** Compares ordered row streams by total count and deterministic chunk hashes. */
public final class BulkMigrationVerifier {
	private BulkMigrationVerifier() {
	}

	public static BulkMigrationVerificationResult verify(final Table expected,
			final Table actual, final int chunkSize) {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(actual, "actual");
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		final List<Column> expectedColumns = expected.getColumns();
		final List<Column> actualColumns = expectedColumns.stream().map(column -> {
			final Column match = actual.getColumns().get(column.getName());
			if (match == null) {
				throw new IllegalArgumentException("Actual table is missing column: " + column.getName());
			}
			return match;
		}).toList();
		final Iterator<Row> expectedRows = expected.getRows().iterator();
		final Iterator<Row> actualRows = actual.getRows().iterator();
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
						BulkMigrationHash.rows(right, actualColumns)));
			}
			return new BulkMigrationVerificationResult(chunkSize, expectedCount, actualCount,
					List.copyOf(chunks));
		} catch (RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			BulkMigrationIteratorSupport.close(failure, expectedRows, actualRows);
		}
	}

	private static List<Row> take(final Iterator<Row> iterator, final int size) {
		final List<Row> rows = new ArrayList<>(size);
		while (rows.size() < size && iterator.hasNext()) {
			rows.add(iterator.next());
		}
		return rows;
	}

}
