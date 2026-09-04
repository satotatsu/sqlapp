/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Restores connections after a read-only multi-connection verification scope. */
final class BulkMigrationVerificationScope implements AutoCloseable {
	private final List<State> states;

	private BulkMigrationVerificationScope(final List<State> states) {
		this.states = states;
	}

	static BulkMigrationVerificationScope open(
			final BulkMigrationVerificationIsolation isolation,
			final Connection... connections) throws SQLException {
		Objects.requireNonNull(isolation, "isolation");
		Objects.requireNonNull(connections, "connections");
		if (isolation.getJdbcLevel() == null) {
			return new BulkMigrationVerificationScope(List.of());
		}
		final List<State> states = new ArrayList<>(connections.length);
		try {
			for (final Connection connection : connections) {
				final Connection value = Objects.requireNonNull(connection, "connection");
				final State state = new State(value, value.getAutoCommit(),
						value.getTransactionIsolation());
				states.add(state);
				value.setTransactionIsolation(isolation.getJdbcLevel());
				value.setAutoCommit(false);
			}
			return new BulkMigrationVerificationScope(List.copyOf(states));
		} catch (SQLException | RuntimeException | Error failure) {
			restore(states, failure);
			throw failure;
		}
	}

	@Override
	public void close() throws SQLException {
		restore(states, null);
	}

	private static void restore(final List<State> states, final Throwable primary)
			throws SQLException {
		SQLException restoreFailure = null;
		for (int i = states.size() - 1; i >= 0; i--) {
			final State state = states.get(i);
			restoreFailure = attempt(state.connection()::rollback, restoreFailure);
			restoreFailure = attempt(() -> state.connection().setAutoCommit(state.autoCommit()),
					restoreFailure);
			restoreFailure = attempt(() -> state.connection()
					.setTransactionIsolation(state.isolation()), restoreFailure);
		}
		if (restoreFailure != null) {
			if (primary != null) {
				primary.addSuppressed(restoreFailure);
			} else {
				throw restoreFailure;
			}
		}
	}

	private static SQLException attempt(final SqlAction action,
			final SQLException previous) {
		try {
			action.run();
			return previous;
		} catch (SQLException failure) {
			if (previous == null) {
				return failure;
			}
			previous.addSuppressed(failure);
			return previous;
		}
	}

	private record State(Connection connection, boolean autoCommit, int isolation) {
	}

	@FunctionalInterface
	private interface SqlAction {
		void run() throws SQLException;
	}
}
