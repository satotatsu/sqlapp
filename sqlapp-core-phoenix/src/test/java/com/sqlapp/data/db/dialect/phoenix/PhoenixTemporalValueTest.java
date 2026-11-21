/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Schema;

class PhoenixTemporalValueTest {
	@Test
	@SuppressWarnings("unchecked")
	void retainsEpochMillisThroughJdbcRowsXmlAndSqlInEveryVersion() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (String nativeType : List.of("DATE", "TIME", "UNSIGNED_DATE", "UNSIGNED_TIME")) {
				final var table = new Table("EVENTS").setDialect(dialect);
				final var schema = new Schema("APP").setProductName("Apache Phoenix").setProductMajorVersion(0)
						.setProductMinorVersion(0).setDialect(dialect);
				schema.getTables().add(table);
				final var column = new Column("VALUE");
				dialect.setDbType(nativeType, null, null, column);
				table.getColumns().add(column);
				final var handler = dialect.getDbDataType(column).getJdbcTypeHandler();
				for (long millis : new long[] { 0, 1732019696789L, 1L }) {
					final Date input = nativeType.endsWith("TIME") ? new java.sql.Time(millis)
							: new java.sql.Date(millis);
					final ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
							new Class<?>[] { ResultSet.class }, (p, method, args) -> input);
					final Date read = (Date) handler.getObject(rs, 1);
					assertEquals(millis, read.getTime());
					final var row = table.newRow();
					row.put("VALUE", read);
					table.getRows().add(row);
					assertEquals(millis, ((Date) row.get("VALUE")).getTime());
					final Object[] bound = new Object[3];
					final PreparedStatement stmt = (PreparedStatement) Proxy.newProxyInstance(
							getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class },
							(p, method, args) -> {
								if (method.getName().equals("setObject"))
									System.arraycopy(args, 0, bound, 0, 3);
								return null;
							});
					handler.setObject(stmt, 1, row.get("VALUE"), column);
					assertEquals(millis, ((Date) bound[1]).getTime());
					assertEquals(column.getDataType() == DataType.DATE ? java.sql.Types.DATE : java.sql.Types.TIME,
							bound[2]);
					final String text = (String) column.getFormatter().format(read);
					assertEquals(millis, ((Date) column.getConverter().convertObject(text)).getTime());
					assertTrue(dialect.getSqlValueDefinition(column, read).contains(text));
				}
				final Schema restoredSchema = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
				final Table restored = restoredSchema.getTables().get("EVENTS");
				assertEquals(1732019696789L, ((Date) restored.getRows().get(1).get("VALUE")).getTime());
				assertEquals(1732019696789L, ((Date) restored.getColumns().get("VALUE").getConverter()
						.convertObject("2024-11-19 12:34:56.789")).getTime());
			}
		}
	}

	@Test
	void convertsJavaTimeWithoutTruncationAndRejectsSubMillisecondLoss() {
		final var dialect = DialectHolder.defaultDialect;
		final var converter = dialect.getDbDataTypes().getDbType(DataType.DATE).getConverter();
		assertNull(converter.convertObject(null));
		assertEquals(1732019696789L,
				((Date) converter.convertObject(Instant.parse("2024-11-19T12:34:56.789Z"))).getTime());
		assertEquals("'2024-11-19 12:34:56.789'",
				dialect.getSqlValueDefinition(new Column("D").setDataType(DataType.DATE),
						java.sql.Timestamp.from(Instant.parse("2024-11-19T12:34:56.789Z"))));
		assertThrows(IllegalArgumentException.class,
				() -> converter.convertObject(Instant.parse("2024-11-19T12:34:56.789001Z")));
		assertThrows(IllegalArgumentException.class,
				() -> converter.convertObject(java.sql.Timestamp.valueOf("2024-11-19 12:34:56.789001")));
		assertThrows(IllegalArgumentException.class, () -> converter.convertObject("not a date"));
	}
}
