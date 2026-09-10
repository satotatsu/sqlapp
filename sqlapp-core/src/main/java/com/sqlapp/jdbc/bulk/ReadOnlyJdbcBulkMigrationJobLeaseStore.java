/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** JDBC lease reader that never creates, updates, or deletes its control table. */
final class ReadOnlyJdbcBulkMigrationJobLeaseStore
		implements BulkMigrationJobLeaseStore {
	private final Connection connection;
	private final String rawTableName;
	private final SqlNode selectNode;

	ReadOnlyJdbcBulkMigrationJobLeaseStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid lease table name: " + tableName);
		}
		final var dialect = DialectResolver.getInstance().getDialect(connection);
		this.rawTableName = tableName;
		this.selectNode = dialect.createSqlFactoryRegistry()
				.createSqlNodes(JdbcBulkMigrationJobLeaseTable.table(tableName),
						SqlType.SELECT).get(0);
	}

	@Override
	public Optional<BulkMigrationJobLease> load(final String jobId)
			throws SQLException {
		JdbcBulkMigrationJobLeaseTable.validateJobId(jobId);
		if (!JdbcBulkMigrationJobLeaseTable.exists(connection, rawTableName)) {
			return Optional.empty();
		}
		JdbcBulkMigrationJobLeaseTable.validateStructure(connection, rawTableName);
		final BulkMigrationJobLease[] result = new BulkMigrationJobLease[1];
		new JdbcHandler(selectNode,
				rs -> result[0] = JdbcBulkMigrationJobLeaseTable.lease(rs,
						jobId)).execute(connection,
					JdbcBulkMigrationJobLeaseTable.parameters(jobId));
		return Optional.ofNullable(result[0]);
	}

	@Override
	public boolean tryAcquire(final BulkMigrationJobLease lease, final Instant now) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

	@Override
	public boolean renew(final BulkMigrationJobLease lease, final Instant now) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

	@Override
	public void release(final String jobId, final String ownerId) {
		throw new UnsupportedOperationException("Read-only lease store");
	}

}
