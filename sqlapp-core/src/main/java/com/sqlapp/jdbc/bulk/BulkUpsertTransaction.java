/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;

/** Transaction ownership and safe completion for bulk operations. */
public final class BulkUpsertTransaction implements AutoCloseable {
	private enum State { ACTIVE, COMMITTED, ROLLED_BACK, COMMIT_FAILED, ROLLBACK_FAILED }

	private final Connection connection;
	private final boolean managed;
	private State state = State.ACTIVE;
	private boolean closed;

	public static BulkUpsertTransaction begin(final Connection connection, final boolean enabled)
			throws SQLException {
		return new BulkUpsertTransaction(connection, enabled);
	}

	private BulkUpsertTransaction(final Connection connection, final boolean enabled) throws SQLException {
		this.connection = java.util.Objects.requireNonNull(connection, "connection");
		managed = enabled && connection.getAutoCommit();
		if (managed) {
			try {
				connection.setAutoCommit(false);
			} catch (SQLException failure) {
				throw contextual("Failed to begin bulk transaction; connection mode may be unknown. Recover before retrying.", failure);
			}
		}
	}

	public boolean isManaged() { return managed; }

	public void commit() throws SQLException {
		if (!managed) return;
		if (closed || state != State.ACTIVE)
			throw new SQLException("Bulk transaction has already attempted completion: " + state);
		state = State.COMMIT_FAILED;
		try {
			connection.commit();
			state = State.COMMITTED;
		} catch (SQLException failure) {
			throw contextual("Bulk transaction commit failed; commit outcome may be unknown. Verify before retrying.", failure);
		}
	}

	/** Rolls back only an owned, unresolved transaction, preserving the original failure. */
	public void rollback(final Throwable failure) {
		if (!managed || closed || (state != State.ACTIVE && state != State.COMMIT_FAILED)) return;
		java.util.Objects.requireNonNull(failure, "failure");
		try {
			rollbackOwned();
		} catch (SQLException | RuntimeException | Error rollback) {
			if (rollback != failure) failure.addSuppressed(rollback instanceof CompletionFailure ? rollback
					: contextual("Bulk transaction rollback failed; connection recovery is required before retrying.", rollback));
		}
	}

	private void rollbackOwned() throws SQLException {
		// Set before the JDBC call so even unchecked driver failures prohibit restoration.
		state = State.ROLLBACK_FAILED;
		try {
			connection.rollback();
			state = State.ROLLED_BACK;
		} catch (SQLException failure) {
			throw contextual("Bulk transaction rollback failed; connection state is unknown. "
					+ "Autocommit restoration and staging cleanup must be skipped; recover or discard the connection.", failure);
		}
	}

	/** Ends uncommitted owned work before cleanup SQL can implicitly commit it. */
	void finishUncommitted() throws SQLException {
		if (managed && !closed && (state == State.ACTIVE || state == State.COMMIT_FAILED)) rollbackOwned();
	}

	boolean hasFailedRollback() {
		return managed && state == State.ROLLBACK_FAILED;
	}

	/** Restores autocommit only after successful commit/rollback; never commits unfinished work. */
	@Override
	public void close() throws SQLException {
		if (closed) return;
		try {
			finishUncommitted();
			if (managed && !hasFailedRollback()) {
				try {
					connection.setAutoCommit(true);
				} catch (SQLException failure) {
					throw contextual("Bulk transaction " + (state == State.COMMITTED ? "committed" : "rolled back")
							+ "; failed to restore autocommit. The connection requires recovery.", failure);
				}
			}
		} finally {
			closed = true;
		}
	}

	/** Fatal completion failures must veto retries even when suppressed by the original error. */
	static boolean hasCompletionFailure(final Throwable failure) {
		final var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
		final var pending = new java.util.ArrayDeque<Throwable>();
		if (failure != null) pending.add(failure);
		while (!pending.isEmpty()) {
			final Throwable current = pending.removeFirst();
			if (!visited.add(current)) continue;
			if (current instanceof CompletionFailure) return true;
			if (current.getCause() != null) pending.add(current.getCause());
			for (Throwable suppressed : current.getSuppressed()) pending.add(suppressed);
			if (current instanceof SQLException sql && sql.getNextException() != null) pending.add(sql.getNextException());
		}
		return false;
	}

	private static SQLException contextual(final String message, final Throwable cause) {
		return new CompletionFailure(message, cause);
	}

	private static final class CompletionFailure extends SQLException {
		private static final long serialVersionUID = 1L;

		private CompletionFailure(final String message, final Throwable cause) {
			super(message, cause instanceof SQLException sql ? sql.getSQLState() : null,
					cause instanceof SQLException sql ? sql.getErrorCode() : 0, cause);
		}
	}
}
