/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import lombok.Value;

/** Count and digest comparison for one verification chunk. */
@Value
public class BulkMigrationVerificationChunk {
	long index;
	int expectedRows;
	int actualRows;
	String expectedHash;
	String actualHash;

	public boolean isMatch() {
		return expectedRows == actualRows && expectedHash.equals(actualHash);
	}
}
