/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mysql;

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
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.MySql570;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** MySQL 5.7 compatibility coverage for the metadata reader tree. */
class MySql57MetadataReaderTest {
	private static final MySQLContainer MYSQL = ReusableTestcontainers
			.configure(new MySQLContainer("mysql:5.7"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MYSQL);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MYSQL);
	}

	@Test
	void readsCoreSchemaObjectsWithThe57Reader() throws SQLException {
		try (Connection connection = MYSQL.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(MySql570.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MYSQL.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MYSQL.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var parent = schema.getTables().get("metadata57_parent");
			assertNotNull(parent);
			assertTrue(parent.getColumns().get("id").isIdentity());
			var primaryKey = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("PRIMARY"));
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			assertEquals("code", parent.getIndexes().get("idx_metadata57_code")
					.getColumns().get(0).getName());

			var child = schema.getTables().get("metadata57_child");
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("fk_metadata57_parent"));
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals(CascadeRule.Cascade, foreignKey.getDeleteRule());

			var view = schema.getViews().get("metadata57_view");
			assertNotNull(view);
			String viewStatement = String.join("\n", view.getStatement()).toLowerCase();
			assertTrue(viewStatement.contains("metadata57_parent"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());

			var procedure = schema.getProcedures().get("metadata57_procedure");
			assertNotNull(procedure);
			assertEquals(ParameterDirection.Input,
					procedure.getArguments().get("p_id").getDirection());
			assertTrue(String.join("\n", procedure.getStatement()).toLowerCase()
					.contains("select code"));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP PROCEDURE IF EXISTS metadata57_procedure");
		statement.execute("DROP VIEW IF EXISTS metadata57_view");
		statement.execute("DROP TABLE IF EXISTS metadata57_child");
		statement.execute("DROP TABLE IF EXISTS metadata57_parent");
		statement.execute("CREATE TABLE metadata57_parent ("
				+ "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
				+ "code VARCHAR(40) NOT NULL, "
				+ "INDEX idx_metadata57_code (code)) ENGINE=InnoDB");
		statement.execute("CREATE TABLE metadata57_child ("
				+ "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
				+ "parent_id BIGINT NOT NULL, "
				+ "CONSTRAINT fk_metadata57_parent FOREIGN KEY (parent_id) "
				+ "REFERENCES metadata57_parent(id) ON DELETE CASCADE) ENGINE=InnoDB");
		statement.execute("CREATE VIEW metadata57_view AS "
				+ "SELECT id, code FROM metadata57_parent");
		statement.execute("CREATE PROCEDURE metadata57_procedure(IN p_id BIGINT) "
				+ "SELECT code FROM metadata57_parent WHERE id = p_id");
	}
}
