/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ResultSetRowIteratorHandlerTest {

	@Test
	void appliesConverterOncePerValueIncludingNull() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:rowiterator", "SA", "");
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery("VALUES (CAST('secret' AS VARCHAR(20)), CAST(NULL AS VARCHAR(20)))")) {
			final Table table = new Table("ITEMS");
			table.getColumns().add(new Column("C1").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("C2").setDataType(DataType.VARCHAR));
			final AtomicInteger calls = new AtomicInteger();
			final var handler = new ResultSetRowIteratorHandler(resultSet, (row, column, value) -> {
				assertSame(table, row.getTable());
				assertSame(table.getColumns().get(column.getName()), column);
				calls.incrementAndGet();
				return value == null ? "missing" : "masked";
			});
			try (var iterator = (AbstractIterator) handler.iterator(table.getRows())) {
				assertTrue(iterator.hasNext());
				assertTrue(iterator.hasNext());
				final var row = iterator.next();
				assertEquals("masked", row.get("C1"));
				assertEquals("missing", row.get("C2"));
				assertEquals(2, calls.get());
				assertFalse(iterator.hasNext());
				assertTrue(resultSet.isClosed());
				assertFalse(connection.isClosed());
			}
		}
	}

	@Test
	void converterFailureClosesResultSetAndPreservesFailure() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:rowiterator", "SA", "");
				var statement = connection.createStatement();
				var resultSet = statement.executeQuery("VALUES (CAST('secret' AS VARCHAR(20)))")) {
			final Table table = new Table("ITEMS");
			table.getColumns().add(new Column("C1").setDataType(DataType.VARCHAR));
			final var failure = new IllegalArgumentException("conversion failed");
			final var handler = new ResultSetRowIteratorHandler(resultSet, (row, column, value) -> { throw failure; });
			try (var iterator = (AbstractIterator) handler.iterator(table.getRows())) {
				assertSame(failure, assertThrows(IllegalArgumentException.class, iterator::next));
				assertTrue(resultSet.isClosed());
				assertFalse(iterator.hasNext());
			}
		}
	}
}
