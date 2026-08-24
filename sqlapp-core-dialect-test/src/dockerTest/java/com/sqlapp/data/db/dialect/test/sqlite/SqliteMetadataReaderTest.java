/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.UniqueConstraint;

/** SQLite integration coverage for the JDBC metadata reader tree. */
class SqliteMetadataReaderTest {
	@Test
	void testReadsRepresentativeSchemaObjectsFromCurrentSqlite() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("""
					CREATE TABLE metadata_parent (
					 id INTEGER PRIMARY KEY AUTOINCREMENT,
					 code TEXT NOT NULL UNIQUE,
					 amount NUMERIC NOT NULL,
					 CONSTRAINT ck_metadata_parent_amount CHECK (amount >= 0))
					""");
			statement.execute("""
					CREATE TABLE metadata_child (
					 id INTEGER PRIMARY KEY,
					 parent_id INTEGER NOT NULL,
					 code TEXT NOT NULL,
					 normalized_code TEXT GENERATED ALWAYS AS (lower(code)) STORED,
					 display_code TEXT GENERATED ALWAYS AS
					  (printf('%s,%s', lower(code), code)) VIRTUAL,
					 CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id)
					  REFERENCES metadata_parent(id) ON DELETE CASCADE)
					""");
			statement.execute("CREATE INDEX idx_metadata_child_code ON metadata_child(code DESC)");
			statement.execute("CREATE INDEX idx_metadata_child_partial ON metadata_child(code) "
					+ "WHERE parent_id > 0 AND code IS NOT NULL");
			statement.execute("CREATE INDEX idx_metadata_child_expression "
					+ "ON metadata_child(lower(code) DESC)");
			statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_parent");
			statement.execute("CREATE TABLE metadata_strict (id INTEGER PRIMARY KEY, value TEXT) STRICT");
			statement.execute("CREATE TABLE metadata_without_rowid "
					+ "(tenant_id TEXT, id TEXT, value TEXT, "
					+ "PRIMARY KEY (tenant_id, id DESC)) WITHOUT ROWID");
			statement.execute("CREATE TABLE metadata_integer_without_rowid "
					+ "(id INTEGER PRIMARY KEY, value TEXT) WITHOUT ROWID");
			statement.execute("CREATE TABLE metadata_inline_desc_pk "
					+ "(id INTEGER PRIMARY KEY DESC, value TEXT)");
			statement.execute("CREATE TABLE metadata_table_desc_pk "
					+ "(id INTEGER, value TEXT, PRIMARY KEY (id DESC))");
			statement.execute("CREATE TABLE metadata_audit (parent_id INTEGER NOT NULL)");
			statement.execute("CREATE TRIGGER trg_metadata_parent AFTER INSERT ON metadata_parent "
					+ "BEGIN INSERT INTO metadata_audit(parent_id) VALUES (NEW.id); END");
			statement.execute("ATTACH DATABASE ':memory:' AS analytics");
			statement.execute("CREATE TABLE analytics.metadata_attached "
					+ "(id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, "
					+ "status TEXT DEFAULT 'active')");
			statement.execute("CREATE VIEW analytics.metadata_attached_view AS "
					+ "SELECT id, code FROM metadata_attached");
			statement.execute("CREATE TABLE analytics.metadata_attached_child "
					+ "(id INTEGER PRIMARY KEY, parent_id INTEGER, "
					+ "FOREIGN KEY (parent_id) REFERENCES metadata_attached(id) "
					+ "ON UPDATE SET NULL ON DELETE CASCADE)");
			statement.execute("CREATE TRIGGER analytics.trg_metadata_attached "
					+ "AFTER UPDATE ON metadata_attached BEGIN SELECT NEW.id; END");
			statement.execute("CREATE TEMP TABLE metadata_temp (id INTEGER PRIMARY KEY)");

			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Sqlite.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			var schemas = reader.getAllFull(connection);
			assertTrue(!schemas.isEmpty());
			var schema = schemas.stream()
					.filter(s -> s.getTables().get("metadata_parent") != null)
					.findFirst().orElseThrow();
			var parent = schema.getTables().get("metadata_parent");
			assertNotNull(parent);
			assertTrue(parent.getColumns().get("id").isIdentity());
			var primaryKey = parent.getConstraints().stream()
					.filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast)
					.filter(UniqueConstraint::isPrimaryKey)
					.findFirst().orElseThrow();
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			var uniqueCode = parent.getConstraints().stream()
					.filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast)
					.filter(constraint -> !constraint.isPrimaryKey())
					.findFirst().orElseThrow();
			assertEquals("code", uniqueCode.getColumns().get(0).getName());
			var amountCheck = parent.getConstraints().stream()
					.filter(CheckConstraint.class::isInstance)
					.map(CheckConstraint.class::cast)
					.filter(constraint -> "ck_metadata_parent_amount"
							.equals(constraint.getName()))
					.findFirst().orElseThrow();
			assertEquals("amount >= 0", amountCheck.getExpression());
			var child = schema.getTables().get("metadata_child");
			assertNotNull(child);
			var normalizedCode = child.getColumns().get("normalized_code");
			assertEquals("lower(code)", normalizedCode.getFormula());
			assertTrue(normalizedCode.isFormulaPersisted());
			var displayCode = child.getColumns().get("display_code");
			assertEquals("printf('%s,%s', lower(code), code)",
					displayCode.getFormula());
			assertFalse(displayCode.isFormulaPersisted());
			var foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals(CascadeRule.Cascade, foreignKey.getDeleteRule());
			var descendingIndex = child.getIndexes().get("idx_metadata_child_code");
			assertNotNull(descendingIndex);
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			assertEquals("parent_id > 0 AND code IS NOT NULL",
					child.getIndexes().get("idx_metadata_child_partial").getWhere());
			var expressionIndex = child.getIndexes().get("idx_metadata_child_expression");
			assertEquals("lower(code)", expressionIndex.getColumns().get(0).getName());
			assertEquals(Order.Desc, expressionIndex.getColumns().get(0).getOrder());
			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			assertTrue(view.getStatement().get(0)
					.startsWith("SELECT id, code FROM metadata_parent"));
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());
			assertEquals(Boolean.TRUE, schema.getTables().get("metadata_strict")
					.getSpecifics().get("strict", Boolean.class));
			assertEquals(Boolean.TRUE, schema.getTables().get("metadata_without_rowid")
					.getSpecifics().get("without_rowid", Boolean.class));
			assertEquals(Boolean.TRUE, schema.getTables().get("metadata_strict")
					.getSpecifics().get("strict", Boolean.class));
			var withoutRowidPrimaryKey = schema.getTables()
					.get("metadata_without_rowid").getConstraints().stream()
					.filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast)
					.filter(UniqueConstraint::isPrimaryKey)
					.findFirst().orElseThrow();
			assertEquals("tenant_id",
					withoutRowidPrimaryKey.getColumns().get(0).getName());
			assertEquals("id", withoutRowidPrimaryKey.getColumns().get(1).getName());
			assertEquals(Order.Desc,
					withoutRowidPrimaryKey.getColumns().get(1).getOrder());
			assertFalse(schema.getTables().get("metadata_integer_without_rowid")
					.getColumns().get("id").isIdentity());
			assertFalse(schema.getTables().get("metadata_inline_desc_pk")
					.getColumns().get("id").isIdentity());
			assertTrue(schema.getTables().get("metadata_table_desc_pk")
					.getColumns().get("id").isIdentity());
			var trigger = schema.getTriggers().get("trg_metadata_parent");
			assertNotNull(trigger);
			assertEquals("metadata_parent", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(trigger.getStatement().get(0).startsWith("INSERT INTO metadata_audit"));
			var attachedSchema = schemas.stream()
					.filter(s -> "analytics".equals(s.getName()))
					.findFirst().orElseThrow();
			var attachedTable = attachedSchema.getTables().get("metadata_attached");
			assertNotNull(attachedTable);
			assertNotNull(attachedTable.getColumns().get("id"));
			assertTrue(attachedTable.getColumns().get("code").isNotNull());
			assertEquals("'active'",
					attachedTable.getColumns().get("status").getDefaultValue());
			assertTrue(attachedTable.getConstraints().stream()
					.filter(UniqueConstraint.class::isInstance)
					.map(UniqueConstraint.class::cast)
					.anyMatch(constraint -> !constraint.isPrimaryKey()
							&& "code".equals(constraint.getColumns().get(0).getName())));
			var attachedForeignKey = attachedSchema.getTables()
					.get("metadata_attached_child").getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", attachedForeignKey.getColumns().get(0).getName());
			assertEquals("id",
					attachedForeignKey.getRelatedColumns().get(0).getName());
			assertEquals(CascadeRule.SetNull, attachedForeignKey.getUpdateRule());
			assertEquals(CascadeRule.Cascade, attachedForeignKey.getDeleteRule());
			var attachedTrigger = attachedSchema.getTriggers().get("trg_metadata_attached");
			assertNotNull(attachedTrigger);
			assertEquals("metadata_attached", attachedTrigger.getTableName());
			assertEquals("AFTER", attachedTrigger.getActionTiming());
			assertTrue(attachedTrigger.getEventManipulation().contains("UPDATE"));
			assertTrue(attachedTrigger.getStatement().get(0).startsWith("SELECT NEW.id"));
			var attachedView = attachedSchema.getViews().get("metadata_attached_view");
			assertNotNull(attachedView);
			assertTrue(attachedView.getStatement().get(0)
					.startsWith("SELECT id, code FROM metadata_attached"));
			assertEquals(2, attachedView.getColumns().size());
			assertEquals("id", attachedView.getColumns().get(0).getName());
			assertEquals("code", attachedView.getColumns().get(1).getName());
			var tempSchema = schemas.stream()
					.filter(s -> "temp".equals(s.getName()))
					.findFirst().orElseThrow();
			assertNotNull(tempSchema.getTables().get("metadata_temp"));
		}
	}
}
