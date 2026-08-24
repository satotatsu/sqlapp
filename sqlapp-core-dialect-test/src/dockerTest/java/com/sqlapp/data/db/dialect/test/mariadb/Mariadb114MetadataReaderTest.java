/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_40;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

/** MariaDB 11.4 coverage for the system-period metadata reader boundary. */
class Mariadb114MetadataReaderTest {
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.4"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	@Test
	void readsSystemPeriodWithThe114Reader() throws SQLException {
		try (Connection connection = MARIADB.createConnection("");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE metadata114_parent ("
					+ "id BIGINT NOT NULL PRIMARY KEY, code VARCHAR(40))");
			statement.execute("CREATE TABLE metadata114_versioned ("
					+ "id BIGINT NOT NULL PRIMARY KEY, value_text VARCHAR(100), "
					+ "row_start TIMESTAMP(6) GENERATED ALWAYS AS ROW START, "
					+ "row_end TIMESTAMP(6) GENERATED ALWAYS AS ROW END, "
					+ "PERIOD FOR SYSTEM_TIME (row_start, row_end)) WITH SYSTEM VERSIONING");
			statement.execute("CREATE VIEW metadata114_view AS "
					+ "SELECT id, code FROM metadata114_parent");

			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Mariadb11_40.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MARIADB.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MARIADB.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var versioned = schema.getTables().get("metadata114_versioned");
			assertNotNull(versioned);
			assertNotNull(versioned.getSystemVersioning());
			assertTrue(versioned.getSystemVersioning().isEnable());
			assertEquals(1, versioned.getTemporalPeriods().size());
			assertEquals("row_start", versioned.getTemporalPeriods().get(0)
					.getStartColumnName());
			assertEquals("row_end", versioned.getTemporalPeriods().get(0)
					.getEndColumnName());

			var view = schema.getViews().get("metadata114_view");
			assertNotNull(view);
			assertTrue(String.join("\n", view.getStatement()).toLowerCase()
					.contains("metadata114_parent"));
			assertEquals(2, view.getColumns().size());
		}
	}
}
