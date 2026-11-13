/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;

/**
 * Reads an in-memory DuckDB database (no external server) and recreates it from
 * the generated DDL.
 */
class DuckDbMetadataRoundTripTest {

	private static final String DDL = """
			CREATE SCHEMA s1;
			CREATE SEQUENCE s1.seq1 START WITH 10 INCREMENT BY 2 MAXVALUE 1000 CYCLE;
			CREATE TABLE s1.parent (
			  id INTEGER PRIMARY KEY
			, code VARCHAR UNIQUE
			, "Name" VARCHAR NOT NULL DEFAULT 'x'
			, amount DECIMAL(10,2) CHECK (amount > 0)
			, mood ENUM('sad', 'o,k', 'it''s')
			, twice INTEGER GENERATED ALWAYS AS (id * 2) VIRTUAL
			, ci VARCHAR COLLATE nocase
			, tags VARCHAR[]
			);
			CREATE TABLE s1.child (
			  cid BIGINT DEFAULT nextval('s1.seq1') PRIMARY KEY
			, pid INTEGER REFERENCES s1.parent(id)
			, a INTEGER
			, b INTEGER
			, UNIQUE (a, b)
			);
			CREATE UNIQUE INDEX ix_child ON s1.child (b, a);
			CREATE INDEX ix_expr ON s1.child ((cid + 1));
			CREATE VIEW s1.v1 AS SELECT id, "Name" FROM s1.parent WHERE id > 0;
			COMMENT ON TABLE s1.parent IS 'parent table';
			COMMENT ON COLUMN s1.parent."Name" IS 'display name';
			""";

	@Test
	void readsSchemaAndRecreatesIt() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			for (final String sql : DDL.split(";")) {
				if (!sql.isBlank()) {
					statement.execute(sql);
				}
			}
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(DuckDb.class, dialect);
			final Schema schema = readSchema(dialect, connection);

			final Sequence sequence = schema.getSequences().get("seq1");
			assertEquals(10, sequence.getStartValue().intValue());
			assertEquals(2, sequence.getIncrementBy().intValue());
			assertEquals(1000, sequence.getMaxValue().intValue());
			assertTrue(sequence.isCycle());

			final Table parent = schema.getTables().get("parent");
			assertEquals("parent table", parent.getRemarks());
			final Column name = parent.getColumns().get("Name");
			assertTrue(name.isNotNull());
			assertEquals("'x'", name.getDefaultValue());
			assertEquals("display name", name.getRemarks());
			assertEquals(DataType.DECIMAL, parent.getColumns().get("amount").getDataType());
			assertEquals(10, parent.getColumns().get("amount").getLength().intValue());
			assertEquals(2, parent.getColumns().get("amount").getScale().intValue());
			final Column mood = parent.getColumns().get("mood");
			assertEquals(DataType.ENUM, mood.getDataType());
			assertEquals(List.of("sad", "o,k", "it's"), List.copyOf(mood.getValues()));
			final Column twice = parent.getColumns().get("twice");
			assertEquals("(id * 2)", twice.getFormula());
			assertNull(twice.getDefaultValue());
			assertEquals("nocase", parent.getColumns().get("ci").getCollation());
			assertEquals("VARCHAR[]", parent.getColumns().get("tags").getDataTypeName());
			assertNotNull(parent.getPrimaryKeyConstraint());
			assertEquals(1, parent.getConstraints().getUniqueConstraints().stream()
					.filter(c -> !c.isPrimaryKey()).count());
			assertEquals(1, parent.getConstraints().getCheckConstraints().size());

			final Table child = schema.getTables().get("child");
			assertEquals("nextval('s1.seq1')", child.getColumns().get("cid").getDefaultValue());
			final ForeignKeyConstraint foreignKey = CommonUtils.first(child.getConstraints().getForeignKeyConstraints());
			assertEquals("parent", foreignKey.getRelatedTableName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			final UniqueConstraint unique = child.getConstraints().getUniqueConstraints().stream()
					.filter(c -> !c.isPrimaryKey()).findFirst().orElseThrow();
			assertEquals(List.of("a", "b"), unique.getColumns().stream().map(c -> c.getName()).toList());
			final Index index = child.getIndexes().get("ix_child");
			assertTrue(index.isUnique());
			assertEquals(List.of("b", "a"), index.getColumns().stream().map(c -> c.getName()).toList());
			final Index expression = child.getIndexes().get("ix_expr");
			assertEquals("((cid + 1))", expression.getSpecifics().get(DuckDbIndexReader.INDEX_KEYS));

			final View view = schema.getViews().get("v1");
			final String definition = String.join("\n", view.getDefinition());
			assertTrue(definition.startsWith("CREATE VIEW"), definition);
			assertFalse(definition.endsWith(";"), definition);

			recreate(dialect, schema);
		}
	}

	private static Schema readSchema(final Dialect dialect, final Connection connection) {
		final var reader = dialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName("s1");
		return CommonUtils.first(reader.getAllFull(connection));
	}

	private static void recreate(final Dialect dialect, final Schema schema) throws Exception {
		final var registry = dialect.createSqlFactoryRegistry();
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA s1");
			execute(statement, registry.createSql(schema.getSequences().get("seq1"), SqlType.CREATE));
			execute(statement, registry.createSql(schema.getTables().get("parent"), SqlType.CREATE));
			execute(statement, registry.createSql(schema.getTables().get("child"), SqlType.CREATE));
			execute(statement, registry.createSql(schema.getViews().get("v1"), SqlType.CREATE));
			final Schema recreated = readSchema(dialect, connection);
			final Table parent = recreated.getTables().get("parent");
			assertEquals("(id * 2)", parent.getColumns().get("twice").getFormula());
			assertEquals(List.of("sad", "o,k", "it's"), List.copyOf(parent.getColumns().get("mood").getValues()));
			assertEquals("nocase", parent.getColumns().get("ci").getCollation());
			assertEquals(1, recreated.getTables().get("child").getConstraints().getForeignKeyConstraints().size());
			assertEquals("((cid + 1))",
					recreated.getTables().get("child").getIndexes().get("ix_expr").getSpecifics()
							.get(DuckDbIndexReader.INDEX_KEYS));
			assertNotNull(recreated.getViews().get("v1"));
		}
	}

	private static void execute(final Statement statement, final List<SqlOperation> operations) throws Exception {
		for (final SqlOperation operation : operations) {
			statement.execute(operation.getSqlText());
		}
	}
}
