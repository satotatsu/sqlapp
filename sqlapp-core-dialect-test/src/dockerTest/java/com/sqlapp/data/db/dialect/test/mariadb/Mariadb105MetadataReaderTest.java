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
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_50;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** MariaDB 10.5 compatibility coverage for the metadata reader tree. */
class Mariadb105MetadataReaderTest {
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:10.5"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	@Test
	void readsCoreSchemaObjectsWithThe105Reader() throws SQLException {
		try (Connection connection = MARIADB.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Mariadb10_50.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MARIADB.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MARIADB.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var parent = schema.getTables().get("metadata105_parent");
			assertNotNull(parent);
			assertTrue(parent.getColumns().get("id").isIdentity());
			var primaryKey = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("PRIMARY"));
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			assertEquals("code", parent.getIndexes().get("idx_metadata105_code")
					.getColumns().get(0).getName());

			var child = schema.getTables().get("metadata105_child");
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("fk_metadata105_parent"));
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals(CascadeRule.Cascade, foreignKey.getDeleteRule());

			var view = schema.getViews().get("metadata105_view");
			assertNotNull(view);
			String viewStatement = String.join("\n", view.getStatement()).toLowerCase();
			assertTrue(viewStatement.contains("metadata105_parent"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());

			var procedure = schema.getProcedures().get("metadata105_procedure");
			assertNotNull(procedure);
			assertEquals(ParameterDirection.Input,
					procedure.getArguments().get("p_id").getDirection());
			assertTrue(String.join("\n", procedure.getStatement()).toLowerCase()
					.contains("select code"));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP PROCEDURE IF EXISTS metadata105_procedure");
		statement.execute("DROP VIEW IF EXISTS metadata105_view");
		statement.execute("DROP TABLE IF EXISTS metadata105_child");
		statement.execute("DROP TABLE IF EXISTS metadata105_parent");
		statement.execute("CREATE TABLE metadata105_parent ("
				+ "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
				+ "code VARCHAR(40) NOT NULL, "
				+ "INDEX idx_metadata105_code (code)) ENGINE=InnoDB");
		statement.execute("CREATE TABLE metadata105_child ("
				+ "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
				+ "parent_id BIGINT NOT NULL, "
				+ "CONSTRAINT fk_metadata105_parent FOREIGN KEY (parent_id) "
				+ "REFERENCES metadata105_parent(id) ON DELETE CASCADE) ENGINE=InnoDB");
		statement.execute("CREATE VIEW metadata105_view AS "
				+ "SELECT id, code FROM metadata105_parent");
		statement.execute("CREATE PROCEDURE metadata105_procedure(IN p_id BIGINT) "
				+ "SELECT code FROM metadata105_parent WHERE id = p_id");
	}
}
