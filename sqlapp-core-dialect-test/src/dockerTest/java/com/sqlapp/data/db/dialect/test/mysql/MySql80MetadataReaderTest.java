/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.MySql801;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.UniqueConstraint;

/** MySQL 8.0 compatibility coverage for its version-specific metadata reader. */
class MySql80MetadataReaderTest {
	private static final MySQLContainer MYSQL = ReusableTestcontainers
			.configure(new MySQLContainer("mysql:8.0"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MYSQL);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MYSQL);
	}

	@Test
	void readsVersionSpecificSchemaObjectsWithThe80Reader() throws SQLException {
		try (Connection connection = MYSQL.createConnection("");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE metadata80_table ("
					+ "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
					+ "code VARCHAR(40) NOT NULL, "
					+ "INDEX idx_metadata80_code (code) INVISIBLE)");
			statement.execute("CREATE VIEW metadata80_view AS "
					+ "SELECT id, code FROM metadata80_table");

			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(MySql801.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MYSQL.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MYSQL.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var table = schema.getTables().get("metadata80_table");
			assertNotNull(table);
			assertTrue(table.getColumns().get("id").isIdentity());
			var primaryKey = assertInstanceOf(UniqueConstraint.class,
					table.getConstraints().get("PRIMARY"));
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			var invisibleIndex = table.getIndexes().get("idx_metadata80_code");
			assertNotNull(invisibleIndex);
			assertEquals("code", invisibleIndex.getColumns().get(0).getName());
			assertFalse(invisibleIndex.isEnable());

			var view = schema.getViews().get("metadata80_view");
			assertNotNull(view);
			assertTrue(String.join("\n", view.getStatement()).toLowerCase()
					.contains("metadata80_table"));
			assertEquals(2, view.getColumns().size());
		}
	}
}
