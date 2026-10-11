/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

/** Persistence and consistency mode for migration checkpoints. */
public enum BulkMigrationCheckpointMode {
	/** Store progress in the target database transaction. */
	DATABASE,
	/** Store progress outside the database, with at-least-once replay semantics. */
	FILE,
	/** Caller-supplied store, typically for embedding or tests. */
	CUSTOM
}
