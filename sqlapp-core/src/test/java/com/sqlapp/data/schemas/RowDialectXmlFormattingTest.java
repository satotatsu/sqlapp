/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.DefaultConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;

class RowDialectXmlFormattingTest {
	@Test
	void usesOptedInDialectFormattingAndHonorsExplicitColumnFormatter() throws Exception {
		final var dialect = new Dialect(() -> null) {
			@Override
			public Converter<?> getColumnValueConverter(Column column) {
				return new DefaultConverter() {
					@Override
					public String format(Object value) {
						return "millis=" + ((java.util.Date) value).getTime();
					}
				};
			}
		};
		final var table = new Table("T").setDialect(dialect);
		final var column = new Column("D").setDataType(DataType.DATE);
		table.getColumns().add(column);
		final var row = table.newRow();
		row.put("D", new java.sql.Date(1234));
		table.getRows().add(row);
		assertTrue(table.asXml().contains("millis=1234"));
		column.setFormatter(value -> "explicit&lt;&");
		assertTrue(table.asXml().contains("explicit&amp;lt;&amp;"));
		row.put("D", null);
		row.putRemarks(column, "null value");
		assertFalse(table.asXml().contains("millis="));
	}

	@Test
	void retainsDefaultFormattingAndBinaryEncodingWithoutAnOptIn() throws Exception {
		final var table = new Table("T");
		table.getColumns().add(new Column("D").setDataType(DataType.DATE));
		table.getColumns().add(new Column("B").setDataType(DataType.VARBINARY));
		final var row = table.newRow();
		row.put("D", java.sql.Date.valueOf("2024-11-19"));
		row.put("B", new byte[] { 0, 1, (byte) 255 });
		table.getRows().add(row);
		final String original = table.asXml();
		table.setDialect(new Dialect(() -> null) { });
		assertEquals(original, table.asXml());
		assertTrue(original.contains("2024-11-19"));
	}
}
