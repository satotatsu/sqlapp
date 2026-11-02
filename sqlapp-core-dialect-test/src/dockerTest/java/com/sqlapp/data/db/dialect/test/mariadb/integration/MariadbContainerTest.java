/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.mariadb.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

/** Container-owned target for metadata/XML/DDL round-trip tests. */
abstract class MariadbContainerTest {
	private static final String ROOT_PASSWORD = "metadata-root";
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer(System.getProperty("sqlapp.test.mariadb.image", "mariadb:11.8"))
					.withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
					.withCommand("--log-bin-trust-function-creators=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	static Connection connect() throws SQLException {
		return DriverManager.getConnection(MARIADB.getJdbcUrl(), "root", ROOT_PASSWORD);
	}
}
