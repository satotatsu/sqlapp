/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * Invoke the selected driver directly, avoiding DriverManager class-loader
 * filtering.
 */
final class AddedDriverDataSource implements DataSource {
	private final Driver driver;
	private final String url;
	private final String user;
	private final String password;

	AddedDriverDataSource(final Driver driver, final PlanningJdbc.Spec spec, final char[] password) {
		this.driver = driver;
		this.url = spec.jdbcUrl();
		this.user = spec.username();
		this.password = new String(password);
	}

	@Override
	public Connection getConnection() throws SQLException {
		return getConnection(user, password);
	}

	@Override
	public Connection getConnection(final String username, final String secret) throws SQLException {
		final var properties = new Properties();
		if (username != null && !username.isBlank())
			properties.setProperty("user", username);
		if (secret != null)
			properties.setProperty("password", secret);
		final var thread = Thread.currentThread();
		final var previousLoader = thread.getContextClassLoader();
		try {
			thread.setContextClassLoader(driver.getClass().getClassLoader());
			final var connection = driver.connect(url, properties);
			if (connection == null)
				throw new SQLException("Added JDBC driver did not accept the connection URL.");
			return connection;
		} catch (final LinkageError failure) {
			throw new SQLException("Added JDBC driver dependency could not be loaded.", failure);
		} finally {
			thread.setContextClassLoader(previousLoader);
		}
	}

	@Override
	public java.io.PrintWriter getLogWriter() {
		return null;
	}

	@Override
	public void setLogWriter(final java.io.PrintWriter writer) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int getLoginTimeout() {
		return 0;
	}

	@Override
	public void setLoginTimeout(final int seconds) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return driver.getParentLogger();
	}

	@Override
	public boolean isWrapperFor(final Class<?> type) {
		return type.isInstance(this);
	}

	@Override
	public <T> T unwrap(final Class<T> type) throws SQLException {
		if (isWrapperFor(type))
			return type.cast(this);
		throw new SQLException("Not a wrapper for " + type.getName());
	}
}
