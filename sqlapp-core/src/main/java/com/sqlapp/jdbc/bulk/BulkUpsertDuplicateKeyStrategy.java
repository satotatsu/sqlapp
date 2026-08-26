/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Handling of duplicate non-null match keys in a bulk-upsert source. */
public enum BulkUpsertDuplicateKeyStrategy {
	/** Reject the input before executing the database merge. */
	ERROR,
	/** Keep the first source row for each key and discard later rows. */
	KEEP_FIRST
}
