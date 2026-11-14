/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationColumnConversionsTest {
	@Test
	void convertsWithValueColumnAndUnconvertedRowContext() {
		var table = table();
		var conversions = BulkMigrationColumnConversions.mvel(table,
				Map.of("NAME", "value.trim().toUpperCase()", "AMOUNT", "row['NAME'].length() + value",
						"NOTE", "value == null ? column : value"));
		var row = conversions.apply(row(table, 1, " ab ", 10, null));
		assertEquals(1, (Integer) row.get("ID"));
		assertEquals("AB", row.get("NAME"));
		// AMOUNT sees the unconverted NAME, and the result uses the column type.
		assertEquals(14, (Integer) row.get("AMOUNT"));
		assertEquals("NOTE", row.get("NOTE"));
		assertEquals(List.of("NAME", "AMOUNT", "NOTE"), List.copyOf(conversions.getExpressions().keySet()));
	}

	@Test
	void fingerprintsAreStableAcrossMapOrderAndChangeWithExpressions() {
		var table = table();
		var first = new LinkedHashMap<String, String>();
		first.put("NOTE", "value");
		first.put("NAME", "value.trim()");
		var second = new LinkedHashMap<String, String>();
		second.put("NAME", "value.trim()");
		second.put("NOTE", "value");
		var a = BulkMigrationColumnConversions.mvel(table, first);
		assertTrue(a.getFingerprint().matches("sha256:[0-9a-f]{64}"), a.getFingerprint());
		assertEquals(a.getFingerprint(), BulkMigrationColumnConversions.mvel(table, second).getFingerprint());
		assertNotEquals(a.getFingerprint(),
				BulkMigrationColumnConversions.mvel(table, Map.of("NAME", "value.strip()", "NOTE", "value"))
						.getFingerprint());
		assertTrue(BulkMigrationColumnConversions.mvel(table, Map.of()).isEmpty());
		assertNull(BulkMigrationColumnConversions.mvel(table, null).getFingerprint());
		// A changed copy type is part of the identity even without expressions.
		var retyped = table.clone();
		retyped.getColumns().get("AMOUNT").setDataType(DataType.VARCHAR).setLength(10);
		var typed = BulkMigrationColumnConversions.of(table, retyped, Map.of());
		assertFalse(typed.isEmpty());
		assertNotNull(typed.getFingerprint());
		retyped.getColumns().get("AMOUNT").setLength(11);
		assertNotEquals(typed.getFingerprint(),
				BulkMigrationColumnConversions.of(table, retyped, Map.of()).getFingerprint());
	}

	@Test
	void projectsRowsIntoLogicalTypesSoConversionsCanChangeTheValueType() {
		var table = table();
		var logical = table.clone();
		logical.getColumns().get("AMOUNT").setDataType(DataType.VARCHAR).setLength(10);
		logical.getColumns().get("NOTE").setDataType(DataType.INT);
		var conversions = BulkMigrationColumnConversions.of(table, logical,
				Map.of("AMOUNT", "value == null ? 'none' : 'A-' + value", "NOTE", "row['NAME'].length()"));
		var source = row(table, 1, "abc", 7, "x");
		var projected = conversions.apply(source);
		assertSame(logical, projected.getTable());
		assertEquals("A-7", projected.get("AMOUNT"));
		assertEquals(3, (Integer) projected.get("NOTE"));
		assertEquals("abc", projected.get("NAME"));
		// The source row is not rewritten.
		assertEquals(7, (Integer) source.get("AMOUNT"));
		// Without an expression the source value is converted to the logical type.
		var plain = BulkMigrationColumnConversions.of(table, logical, Map.of());
		assertEquals("7", plain.apply(row(table, 1, "abc", 7, "12")).get("AMOUNT"));
		assertThrows(IllegalStateException.class, () -> plain.apply(row(table, 1, "abc", 7, "not a number")));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> plain.requireUnconverted(List.of("AMOUNT"), "keyset")).getMessage()
				.contains("Copy types cannot change keyset column T.AMOUNT"));
		var renamed = table.clone();
		renamed.getColumns().get("NOTE").setName("MEMO");
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationColumnConversions.of(table, renamed, null));
	}

	@Test
	void rejectsUnknownBlankAndInvalidEntriesAndKeyColumns() {
		var table = table();
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationColumnConversions.mvel(table, Map.of("MISSING", "value"))).getMessage()
				.contains("T.MISSING"));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationColumnConversions.mvel(table, Map.of("NAME", " ")));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationColumnConversions.mvel(table, Map.of("NAME", "value.("))).getMessage()
				.contains("Invalid column conversion expression for T.NAME"));
		var conversions = BulkMigrationColumnConversions.mvel(table, Map.of("NAME", "value"));
		conversions.requireUnconverted(List.of("ID"), "primary key");
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> conversions.requireUnconverted(List.of("name"), "keyset")).getMessage()
				.contains("keyset column T.NAME"));
	}

	@Test
	void failuresNameTheColumnButNotTheValueAndCloseTheSource() throws Exception {
		var table = table();
		var conversions = BulkMigrationColumnConversions.mvel(table, Map.of("NAME", "value.substring(100)"));
		var closed = new AtomicBoolean();
		var rows = List.of(row(table, 1, "secret-value", 1, null));
		Iterator<Row> source = new CloseableIterator(rows.iterator(), closed);
		var iterator = conversions.iterator(source);
		var failure = assertThrows(IllegalStateException.class, iterator::next);
		assertTrue(failure.getMessage().contains("T.NAME"), failure.getMessage());
		assertFalse(failure.getMessage().contains("secret-value"), failure.getMessage());
		assertTrue(closed.get());
	}

	@Test
	void wrapsLazyRowHandlersAndLeavesUnconvertedSourcesUnchanged() {
		var table = table();
		var conversions = BulkMigrationColumnConversions.mvel(table, Map.of("NAME", "value + '!'"));
		var plain = table.clone();
		plain.setRowIteratorHandler(rows -> List.of(row(plain, 1, "a", 1, null), row(plain, 2, "b", 2, null))
				.iterator());
		var converted = plain.clone();
		converted.setRowIteratorHandler(conversions.rowIteratorHandler(rows -> List
				.of(row(converted, 1, "a", 1, null), row(converted, 2, "b", 2, null)).iterator()));
		var names = new ArrayList<Object>();
		converted.getRows().forEach(row -> names.add(row.get("NAME")));
		assertEquals(List.of("a!", "b!"), names);
		com.sqlapp.data.schemas.RowIteratorHandler handler = rows -> List.<Row>of().iterator();
		assertSame(handler, BulkMigrationColumnConversions.mvel(table, Map.of()).rowIteratorHandler(handler));
	}

	private static Table table() {
		var table = new Table("T");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		table.getColumns().add(new Column("AMOUNT").setDataType(DataType.INT));
		table.getColumns().add(new Column("NOTE").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey(table.getColumns().get("ID"));
		return table;
	}

	private static Row row(Table table, Integer id, String name, Integer amount, String note) {
		var row = table.newRow();
		row.put("ID", id);
		row.put("NAME", name);
		row.put("AMOUNT", amount);
		row.put("NOTE", note);
		return row;
	}

	private static final class CloseableIterator implements Iterator<Row>, AutoCloseable {
		private final Iterator<Row> delegate;
		private final AtomicBoolean closed;

		private CloseableIterator(Iterator<Row> delegate, AtomicBoolean closed) {
			this.delegate = delegate;
			this.closed = closed;
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public Row next() {
			return delegate.next();
		}

		@Override
		public void close() {
			closed.set(true);
		}
	}
}
