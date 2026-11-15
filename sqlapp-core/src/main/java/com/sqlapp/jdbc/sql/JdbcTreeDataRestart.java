/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import java.util.List;
import java.util.Objects;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.TransactionalBulkMigrationCheckpointStore;

/**
 * How a {@link JdbcTreeDataSession} continues after periodic commits and after
 * an interrupted execution.
 *
 * <p>Root SELECT statements are free-form SQL, so the session cannot rewrite them
 * to skip processed roots. Instead the SQL cooperates and the session guarantees
 * consistency:</p>
 * <ul>
 * <li>{@link #none()} (default): no restart support. Periodic commits require a
 * root cursor that stays open across commits.</li>
 * <li>{@link #selfExcluding()}: the root SELECT itself excludes roots that were
 * already processed (for example with {@code NOT EXISTS}). The session may
 * re-execute it after a commit.</li>
 * <li>{@link #checkpoint(String, List, TransactionalBulkMigrationCheckpointStore)}:
 * the root rows are ordered by declared unique key columns. The last processed key
 * is saved in the same transaction as the business changes and is available to the
 * root SQL as the {@code checkpoint} variable.</li>
 * </ul>
 */
public sealed interface JdbcTreeDataRestart {

	/** No restart support; the default. */
	static JdbcTreeDataRestart none() {
		return None.INSTANCE;
	}

	/**
	 * The root SELECT excludes already processed roots, so re-executing it after a
	 * commit or after an interrupted execution continues with unprocessed roots.
	 * The session cannot verify this; it is the SQL author's contract.
	 */
	static JdbcTreeDataRestart selfExcluding() {
		return SelfExcluding.INSTANCE;
	}

	/**
	 * Restarts from the last committed root key.
	 *
	 * <p>The root SELECT must return the key columns in strictly increasing order
	 * and must use the {@code checkpoint} variable, which is {@code null} on the
	 * first run and otherwise a map of the last processed key values by column
	 * name, for example:</p>
	 *
	 * <pre>
	 * WHERE 1=1
	 *   /&#42;if checkpoint != null&#42;/ AND c.ID &gt; /&#42;checkpoint.CUSTOMER_ID&#42;/0 /&#42;end&#42;/
	 * GROUP BY c.ID
	 * ORDER BY c.ID
	 * </pre>
	 *
	 * <p>The session rejects rows that are not strictly increasing, saves the last
	 * key before every commit in the same transaction, and rejects a checkpoint whose
	 * root SQL or key columns changed. A completed checkpoint processes no further
	 * roots until it is deleted from the store.</p>
	 *
	 * @param jobId      checkpoint identity
	 * @param keyColumns root table columns that identify and order root rows
	 * @param store      checkpoint store that participates in the session
	 *                   connection's transaction
	 */
	static JdbcTreeDataRestart checkpoint(final String jobId, final List<String> keyColumns,
			final TransactionalBulkMigrationCheckpointStore store) {
		return new Checkpoint(jobId, keyColumns, store);
	}

	/** Whether the root SELECT may be re-executed to continue after a commit. */
	boolean reopensRootCursor();

	record None() implements JdbcTreeDataRestart {
		static final None INSTANCE = new None();

		@Override
		public boolean reopensRootCursor() {
			return false;
		}
	}

	record SelfExcluding() implements JdbcTreeDataRestart {
		static final SelfExcluding INSTANCE = new SelfExcluding();

		@Override
		public boolean reopensRootCursor() {
			return true;
		}
	}

	record Checkpoint(String jobId, List<String> keyColumns, TransactionalBulkMigrationCheckpointStore store)
			implements JdbcTreeDataRestart {
		public Checkpoint {
			BulkMigrationCheckpoint.validateMigrationId(jobId);
			Objects.requireNonNull(keyColumns, "keyColumns");
			if (keyColumns.isEmpty() || keyColumns.stream().anyMatch(name -> name == null || name.isBlank())) {
				throw new IllegalArgumentException("checkpoint restart requires key column names");
			}
			keyColumns = List.copyOf(keyColumns);
			Objects.requireNonNull(store, "store");
		}

		@Override
		public boolean reopensRootCursor() {
			return true;
		}
	}
}
