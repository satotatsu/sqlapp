/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Atomic persistence contract keyed by a stable migration job ID. The stored
 * plan fingerprint identifies the exact configuration currently holding it.
 */
public interface BulkMigrationJobLeaseStore {
	Optional<BulkMigrationJobLease> load(String jobId) throws SQLException;

	/** Acquires an absent or expired lease atomically. */
	boolean tryAcquire(BulkMigrationJobLease lease, Instant now) throws SQLException;

	/** Renews only the same owner's unexpired lease atomically. */
	boolean renew(BulkMigrationJobLease lease, Instant now) throws SQLException;

	/** Releases only when both job and owner still match. */
	void release(String jobId, String ownerId) throws SQLException;
}
