/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;

/** Transaction scope used by bulk-upsert executors without DDL commit caveats. */
public final class BulkUpsertTransaction implements AutoCloseable {
	private final Connection connection;
	private final boolean managed;

	public static BulkUpsertTransaction begin(final Connection connection, final boolean enabled)
			throws SQLException {
		return new BulkUpsertTransaction(connection, enabled);
	}

	private BulkUpsertTransaction(final Connection connection, final boolean enabled) throws SQLException {
		this.connection = java.util.Objects.requireNonNull(connection, "connection");
		managed = enabled && connection.getAutoCommit();
		if (managed) connection.setAutoCommit(false);
	}

	public boolean isManaged() { return managed; }

	public void commit() throws SQLException {
		if (managed) connection.commit();
	}

	public void rollback(final Throwable failure) {
		if (!managed) return;
		try { connection.rollback(); }
		catch (SQLException e) { failure.addSuppressed(e); }
	}

	@Override
	public void close() throws SQLException {
		if (managed) connection.setAutoCommit(true);
	}
}
