/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.bulk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.vertica.jdbc.VerticaConnection;
import com.vertica.jdbc.VerticaCopyStream;

/** Vertica bulk insert backed by VerticaCopyStream. */
public class VirticaBulkInsertExecutor implements BulkInsertExecutor {
	private final Dialect dialect;

	public VirticaBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		try (VirticaBulkDataInputStream input =
				new VirticaBulkDataInputStream(table, options)) {
			final StringBuilder sql = new StringBuilder("COPY ")
					.append(dialect.getObjectFullName(table.getSchemaName(),
							table.getName())).append(" (");
			for (int i = 0; i < input.getColumns().size(); i++) {
				if (i > 0) {
					sql.append(", ");
				}
				sql.append(dialect.quote(input.getColumns().get(i).getName()));
			}
			// Separate non-printing markers preserve embedded line feeds and also
			// distinguish NULL from an empty string.
			sql.append(") FROM STDIN DELIMITER E'\\037' "
					+ "RECORD TERMINATOR E'\\036' NULL AS E'\\035' "
					+ "ESCAPE AS E'\\\\'");
			final VerticaCopyStream stream = new VerticaCopyStream(
					connection.unwrap(VerticaConnection.class), sql.toString(), input);
			stream.start();
			return stream.finish();
		} catch (IOException e) {
			throw new SQLException("Failed to stream Vertica COPY rows for "
					+ table.getName(), e);
		}
	}

	private void validateOptions(final BulkOption options) {
		if (options != null && (options.getBatchSize() != null
				|| options.getBulkCopyTimeout() != null
				|| options.isAllowEncryptedValueModifications()
				|| options.isFireTriggers() || options.isTableLock()
				|| options.isUseTransaction())) {
			throw new IllegalArgumentException(
					"Vertica COPY does not support the requested SQL Server-style bulk options");
		}
	}
}
