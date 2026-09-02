/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;

import lombok.Value;

/** Full count and chunk-hash verification result. */
@Value
public class BulkMigrationVerificationResult {
	int chunkSize;
	long expectedRows;
	long actualRows;
	List<String> columns;
	String expectedKeysetFingerprint;
	String actualKeysetFingerprint;
	List<BulkMigrationVerificationChunk> chunks;

	public BulkMigrationVerificationResult(final int chunkSize, final long expectedRows,
			final long actualRows, final List<BulkMigrationVerificationChunk> chunks) {
		this(chunkSize, expectedRows, actualRows, List.of(), chunks);
	}

	public BulkMigrationVerificationResult(final int chunkSize, final long expectedRows,
			final long actualRows, final List<String> columns,
			final List<BulkMigrationVerificationChunk> chunks) {
		this(chunkSize, expectedRows, actualRows, columns, null, null, chunks);
	}

	public BulkMigrationVerificationResult(final int chunkSize, final long expectedRows,
			final long actualRows, final List<String> columns,
			final String expectedKeysetFingerprint,
			final String actualKeysetFingerprint,
			final List<BulkMigrationVerificationChunk> chunks) {
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be greater than zero");
		}
		if (expectedRows < 0 || actualRows < 0) {
			throw new IllegalArgumentException("row counts must not be negative");
		}
		Objects.requireNonNull(columns, "columns");
		if (columns.stream().anyMatch(name -> name == null || name.isBlank())
				|| new HashSet<>(columns).size() != columns.size()) {
			throw new IllegalArgumentException(
					"Verification columns must be non-empty and unique");
		}
		if ((expectedKeysetFingerprint == null) != (actualKeysetFingerprint == null)
				|| expectedKeysetFingerprint != null
						&& (expectedKeysetFingerprint.isBlank()
								|| actualKeysetFingerprint.isBlank())) {
			throw new IllegalArgumentException(
					"Keyset fingerprints must be supplied together and must not be empty");
		}
		final List<BulkMigrationVerificationChunk> copy = List.copyOf(
				Objects.requireNonNull(chunks, "chunks"));
		long chunkExpectedRows = 0;
		long chunkActualRows = 0;
		for (int i = 0; i < copy.size(); i++) {
			final BulkMigrationVerificationChunk chunk = copy.get(i);
			if (chunk.getIndex() != i) {
				throw new IllegalArgumentException(
						"chunk indexes must be contiguous from zero: " + chunk.getIndex());
			}
			if (chunk.getExpectedRows() > chunkSize || chunk.getActualRows() > chunkSize) {
				throw new IllegalArgumentException(
						"chunk row count exceeds chunkSize at index " + i);
			}
			chunkExpectedRows += chunk.getExpectedRows();
			chunkActualRows += chunk.getActualRows();
		}
		if (chunkExpectedRows != expectedRows || chunkActualRows != actualRows) {
			throw new IllegalArgumentException(
					"chunk row counts do not match verification totals");
		}
		this.chunkSize = chunkSize;
		this.expectedRows = expectedRows;
		this.actualRows = actualRows;
		this.columns = List.copyOf(columns);
		this.expectedKeysetFingerprint = expectedKeysetFingerprint;
		this.actualKeysetFingerprint = actualKeysetFingerprint;
		this.chunks = copy;
	}

	public boolean isMatch() {
		return expectedRows == actualRows
				&& chunks.stream().allMatch(BulkMigrationVerificationChunk::isMatch);
	}

	public List<BulkMigrationVerificationChunk> getMismatches() {
		return chunks.stream().filter(chunk -> !chunk.isMatch()).toList();
	}
}
