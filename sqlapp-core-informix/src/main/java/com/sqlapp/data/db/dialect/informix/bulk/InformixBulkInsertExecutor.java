/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertMethod;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor;

/** Informix JDBC batches use the driver's PUT transport when IFX_USEPUT=1. */
public class InformixBulkInsertExecutor extends JdbcBatchBulkInsertExecutor {
	private static final Pattern USE_PUT = Pattern.compile("(?:^|[;:?&])IFX_USEPUT=([^;?&]*)",
			Pattern.CASE_INSENSITIVE);

	public InformixBulkInsertExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		if (options != null && options.getInsertMethod() == BulkInsertMethod.NATIVE && !usesPut(connection))
			throw new IllegalArgumentException(
					"Informix NATIVE inserts require a connection configured with IFX_USEPUT=1; "
							+ "use AUTO or JDBC_BATCH for ordinary batches. No connection setting was changed.");
		return super.execute(connection, table, options);
	}

	@Override
	protected long countBatchRows(final int[] counts) throws SQLException {
		// PUT returns the batch total first, followed by SUCCESS_NO_INFO for
		// the remaining entries. Each individual INSERT can insert only one row.
		if (counts.length > 1 && counts[0] > 1) {
			for (int i = 1; i < counts.length; i++) {
				if (counts[i] != Statement.SUCCESS_NO_INFO)
					return super.countBatchRows(counts);
			}
			return counts[0];
		}
		return super.countBatchRows(counts);
	}

	private boolean usesPut(final Connection connection) throws SQLException {
		// Driver state is authoritative when a session changed the setting after
		// connect.
		final Boolean driverValue = driverUsePut(connection);
		if (driverValue != null)
			return driverValue;
		String value = null;
		try {
			value = connection.getClientInfo("IFX_USEPUT");
		} catch (SQLFeatureNotSupportedException unsupported) {
			/* The JDBC URL is the alternate source. */ }
		if (value != null)
			return "1".equals(value) || "true".equalsIgnoreCase(value);
		final String url = connection.getMetaData().getURL();
		if (url == null)
			return false;
		final var matcher = USE_PUT.matcher(url);
		boolean enabled = false;
		while (matcher.find())
			enabled = "1".equals(matcher.group(1)) || "true".equalsIgnoreCase(matcher.group(1));
		return enabled;
	}

	private Boolean driverUsePut(final Connection connection) throws SQLException {
		try {
			try {
				return (Boolean) connection.getClass().getMethod("getIFX_USEPUT").invoke(connection);
			} catch (NoSuchMethodException wrapper) {
				// Keep the JDBC driver optional; use its public getter through JDBC unwrapping.
				final Class<? extends Connection> driver;
				try {
					driver = Class.forName("com.informix.jdbc.IfxSqliConnect", false,
							Thread.currentThread().getContextClassLoader()).asSubclass(Connection.class);
				} catch (ClassNotFoundException absent) {
					return null;
				}
				if (!connection.isWrapperFor(driver))
					return null;
				return (Boolean) driver.getMethod("getIFX_USEPUT").invoke(connection.unwrap(driver));
			}
		} catch (java.lang.reflect.InvocationTargetException failure) {
			if (failure.getCause() instanceof SQLException sqlFailure)
				throw sqlFailure;
			throw new SQLException("Cannot verify Informix IFX_USEPUT driver state", failure.getCause());
		} catch (ReflectiveOperationException failure) {
			throw new SQLException("Cannot verify Informix IFX_USEPUT driver state", failure);
		}
	}
}
