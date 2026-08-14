/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.db2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.db2.Db2Container;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

/** Db2 container readiness based on an actual SQL round trip. */
final class SqlReadyDb2Container extends Db2Container {
	SqlReadyDb2Container(final String image) {
		super(image);
		acceptLicense();
		withReuse(ReusableTestcontainers.isReuseEnabled());
	}

	@Override
	protected void waitUntilContainerStarted() {
		DriverManager.setLoginTimeout(2);
		final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
		SQLException lastException = null;
		while (System.nanoTime() < deadline) {
			try (Connection connection = DriverManager.getConnection(
					getJdbcUrl(), getUsername(), getPassword());
					Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery(
							"SELECT 1 FROM SYSIBM.SYSDUMMY1")) {
				if (resultSet.next()) {
					return;
				}
			} catch (SQLException e) {
				lastException = e;
			}
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new ContainerLaunchException(
						"Interrupted while waiting for Db2 readiness.", e);
			}
		}
		throw new ContainerLaunchException(
				"Db2 did not accept JDBC connections within 10 minutes.",
				lastException);
	}
}
