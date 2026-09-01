/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Value;

/** Count and digest comparison for one verification chunk. */
@Value
public class BulkMigrationVerificationChunk {
	long index;
	int expectedRows;
	int actualRows;
	String expectedHash;
	String actualHash;
	String expectedFirstKey;
	String expectedLastKey;
	String actualFirstKey;
	String actualLastKey;

	public BulkMigrationVerificationChunk(final long index, final int expectedRows,
			final int actualRows, final String expectedHash, final String actualHash) {
		this(index, expectedRows, actualRows, expectedHash, actualHash,
				null, null, null, null);
	}

	public BulkMigrationVerificationChunk(final long index, final int expectedRows,
			final int actualRows, final String expectedHash, final String actualHash,
			final String expectedFirstKey, final String expectedLastKey,
			final String actualFirstKey, final String actualLastKey) {
		if (index < 0) {
			throw new IllegalArgumentException("chunk index must not be negative");
		}
		if (expectedRows < 0 || actualRows < 0) {
			throw new IllegalArgumentException("chunk row counts must not be negative");
		}
		this.index = index;
		this.expectedRows = expectedRows;
		this.actualRows = actualRows;
		this.expectedHash = Objects.requireNonNull(expectedHash, "expectedHash");
		this.actualHash = Objects.requireNonNull(actualHash, "actualHash");
		this.expectedFirstKey = expectedFirstKey;
		this.expectedLastKey = expectedLastKey;
		this.actualFirstKey = actualFirstKey;
		this.actualLastKey = actualLastKey;
	}

	public boolean isMatch() {
		return expectedRows == actualRows && expectedHash.equals(actualHash);
	}
}
