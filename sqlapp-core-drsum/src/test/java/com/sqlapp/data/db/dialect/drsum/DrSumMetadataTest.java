/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum;

import static com.sqlapp.data.db.dialect.drsum.FakeJdbc.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.drsum.metadata.DrSumIndexReader;
import com.sqlapp.data.db.dialect.drsum.metadata.DrSumTableReader;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

/**
 * Metadata reader tests against an in-memory {@code __all_tables__} /
 * {@code __all_indexes__}. The system table values used here follow the
 * documented column names; their exact encodings are assumptions recorded in
 * the readers.
 */
class DrSumMetadataTest {

	private final List<Map<String, Object>> allTables = new ArrayList<>();
	private final List<Map<String, Object>> allIndexes = new ArrayList<>();
	private final AtomicInteger calls = new AtomicInteger();
	private final List<String> sqls = new ArrayList<>();

	private void table(final String table, final String comments) {
		allTables.add(tableRow(table, null, null, comments, null, null, null, null, null, null));
	}

	private void column(final String table, final String column, final String type, final Long precision,
			final Integer scale, final String notNull, final String unique, final String comments) {
		allTables.add(tableRow(table, column, type, comments, notNull, unique, precision, scale, null, null));
	}

	private void viewColumn(final String view, final String column, final String type, final String select) {
		allTables.add(tableRow(view, column, type, null, "0", "0", null, null, select, null));
	}

	private static Map<String, Object> tableRow(final String table, final String column, final String type,
			final String comments, final String notNull, final String unique, final Long precision,
			final Integer scale, final String viewSelect, final String viewConnect) {
		return row("table_name", table, "column_name", column, "assortment", viewSelect == null ? "1" : "2",
				"comments", comments, "view_connect", viewConnect, "view_select", viewSelect, "column_type", type,
				"column_null", notNull, "column_unique", unique, "column_precision", precision, "column_scale",
				scale);
	}

	private Connection connection() {
		return FakeJdbc.connection(calls, sqls, sql -> {
			if (sql.contains("__all_indexes__")) {
				return allIndexes;
			}
			return allTables.stream().filter(r -> !sql.contains("view_select IS NULL") || r.get("view_select") == null)
					.filter(r -> !sql.contains("view_select IS NOT NULL") || r.get("view_select") != null)
					.filter(r -> !sql.contains("column_name IS NOT NULL") || r.get("column_name") != null)
					.filter(r -> !sql.contains("column_unique IS NOT NULL") || r.get("column_unique") != null)
					.collect(Collectors.toList());
		});
	}

	private List<Table> readTables() {
		final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.getReaderOptions().setExcludeSystemObjects(true);
		return reader.getAllFull(connection());
	}

	@Test
	void readsTablesColumnsKeysAndIndexesInBulk() throws Exception {
		for (final String name : List.of("T1", "T2", "T3")) {
			table(name, "comment " + name);
			column(name, "ID", "NUMERIC", 33L, 0, "1", "1", "id");
			column(name, "NAME", "VARCHAR", 20L, 0, "0", "0", null);
			column(name, "MEMO", "VARCHAR", 0L, 0, "N", "N", null);
			column(name, "AMT", "NUMERIC", 10L, 2, "", "", null);
			column(name, "F", "NUMERIC", 0L, 0, null, null, null);
			column(name, "R", "REAL", null, null, "0", "0", null);
			column(name, "D", "DATE", null, null, "0", "0", null);
			column(name, "TS", "TIMESTAMP", 3L, null, "0", "0", null);
			column(name, "IV", "INTERVAL", null, null, "0", "0", null);
			allIndexes.add(row("index_name", "IX_" + name, "table_name", name, "column_name", "D", "status", "0"));
		}
		table("__all_tables__", null);
		column("__all_tables__", "table_name", "VARCHAR", 63L, 0, "0", "0", null);
		allTables.add(tableRow("MV", null, null, null, null, null, null, null, null, "connect"));

		final List<Table> tables = readTables();
		assertEquals(4, calls.get(), "tables, columns, unique keys and indexes are each read once: " + sqls);
		assertEquals(List.of("T1", "T2", "T3"), tables.stream().map(Table::getName).collect(Collectors.toList()));
		final Table table = tables.get(0);
		assertEquals("comment T1", table.getRemarks());
		assertEquals("1", table.getSpecifics().get(DrSumTableReader.ASSORTMENT));
		assertEquals(List.of("ID", "NAME", "MEMO", "AMT", "F", "R", "D", "TS", "IV"),
				table.getColumns().stream().map(Column::getName).collect(Collectors.toList()));
		final Column id = table.getColumns().get("ID");
		assertEquals(DataType.NUMERIC, id.getDataType());
		assertEquals(33L, id.getLength());
		assertTrue(id.isNotNull());
		assertEquals("id", id.getRemarks());
		assertEquals(DataType.VARCHAR, table.getColumns().get("NAME").getDataType());
		assertEquals(20L, table.getColumns().get("NAME").getLength());
		assertFalse(table.getColumns().get("NAME").isNotNull());
		assertEquals(DataType.LONGVARCHAR, table.getColumns().get("MEMO").getDataType());
		assertEquals(65535L, table.getColumns().get("MEMO").getLength());
		assertEquals(2, table.getColumns().get("AMT").getScale());
		assertNull(table.getColumns().get("F").getLength());
		assertEquals(DataType.REAL, table.getColumns().get("R").getDataType());
		assertEquals(DataType.DATE, table.getColumns().get("D").getDataType());
		assertEquals(DataType.TIMESTAMP, table.getColumns().get("TS").getDataType());
		assertEquals(3L, table.getColumns().get("TS").getLength());
		assertEquals(DataType.INTERVAL_DAY_TO_SECOND, table.getColumns().get("IV").getDataType());
		assertEquals(1, table.getConstraints().getUniqueConstraints().size());
		assertEquals("ID", table.getConstraints().getUniqueConstraints().get(0).getColumns().get(0).getName());
		assertEquals("0", table.getIndexes().get("IX_T1").getSpecifics().get(DrSumIndexReader.STATUS));

		final Table copy = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final String create = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(copy, SqlType.CREATE)
				.stream().map(SqlOperation::getSqlText).collect(Collectors.joining(";\n"));
		for (final String expected : List.of("CREATE TABLE T1", "ID NUMERIC(33,0) NOT NULL UNIQUE",
				"NAME VARCHAR(20)", "MEMO VARCHAR", "AMT NUMERIC(10,2)", "F NUMERIC", "TS TIMESTAMP(3)",
				"IV INTERVAL", "CREATE INDEX IX_T1 ON T1( D)", "ALTER TABLE T1 MODIFY COMMENT 'comment T1'",
				"ALTER TABLE T1 MODIFY ID COMMENT 'id'")) {
			assertTrue(create.contains(expected), expected + " in " + create);
		}
		assertFalse(create.contains("VARCHAR(255)"), create);
	}

	@Test
	void rejectsUnknownFlagEncodingInsteadOfGuessing() {
		table("T", null);
		column("T", "A", "NUMERIC", 10L, 0, "maybe", "0", null);
		assertThrows(RuntimeException.class, this::readTables);
	}

	@Test
	void readsViewStatementOrDefinitionAndColumns() {
		viewColumn("V1", "A", "NUMERIC", "SELECT A FROM T");
		viewColumn("V1", "B", "VARCHAR", "SELECT A FROM T");
		viewColumn("V2", "X", "NUMERIC", "CREATE VIEW V2 AS SELECT X FROM T");
		final List<Table> views = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader()
				.getAllFull(connection());
		assertEquals(2, calls.get(), "views and their columns are each read once: " + sqls);
		final View v1 = (View) views.get(0);
		assertEquals(List.of("SELECT A FROM T"), v1.getStatement());
		assertEquals(List.of("A", "B"), v1.getColumns().stream().map(Column::getName).collect(Collectors.toList()));
		final View v2 = (View) views.get(1);
		assertEquals("CREATE VIEW V2 AS SELECT X FROM T", String.join("", v2.getDefinition()));
		assertTrue(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(v1, SqlType.CREATE).get(0)
				.getSqlText().contains("SELECT A FROM T"));
	}

	@Test
	void rejectsInconsistentViewText() {
		viewColumn("V1", "A", "NUMERIC", "SELECT A FROM T");
		viewColumn("V1", "B", "NUMERIC", "SELECT B FROM T");
		assertThrows(RuntimeException.class,
				() -> DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader()
						.getAllFull(connection()));
	}
}
