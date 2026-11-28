/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.PostgresIntervalCodec;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class PostgresBulkIntervalTest {
	@Test
	void copyUsesTheSameExactEncodingAsJdbc() throws Exception {
		final Table table = table(false);
		final IntervalValue value = IntervalValue.parse("P-14M3DT90000.123456S");
		final var row = table.newRow();
		row.put(0, value);
		table.getRows().add(row);
		try (var reader = new PostgresBulkDataReader(table, null)) {
			final var output = new StringWriter();
			reader.transferTo(output);
			assertEquals("\"" + PostgresIntervalCodec.encode(value) + "\"\n", output.toString());
		}
	}

	@Test
	void copyArrayPreservesNullsAndIndependentComponents() throws Exception {
		final Table table = table(true);
		final IntervalValue value = IntervalValue.parse("P1DT-1H");
		final var row = table.newRow();
		row.put(0, new IntervalValue[] { value, null });
		table.getRows().add(row);
		try (var reader = new PostgresBulkDataReader(table, null)) {
			final var output = new StringWriter();
			reader.transferTo(output);
			assertTrue(output.toString().contains(PostgresIntervalCodec.encode(value)));
			assertTrue(output.toString().contains("NULL"));
		}
	}

	@Test
	void copyRejectsColumnPrecisionLoss() throws Exception {
		final Table table = table(false);
		table.getColumns().get(0).setLength(3);
		final var row = table.newRow();
		row.put(0, IntervalValue.parse("PT0.123456S"));
		table.getRows().add(row);
		try (var reader = new PostgresBulkDataReader(table, null)) {
			assertThrows(ArithmeticException.class, () -> reader.read(new char[1024]));
		}
	}

	private Table table(final boolean array) {
		final Table table = new Table("durations");
		table.getColumns().add(new Column("amount").setDataType(DataType.INTERVAL).setArrayDimension(array ? 1 : 0));
		return table;
	}
}
