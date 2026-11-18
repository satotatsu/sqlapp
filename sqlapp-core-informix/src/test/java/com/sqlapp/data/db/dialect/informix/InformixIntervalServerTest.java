/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

@EnabledIfSystemProperty(named="sqlapp.interval.ifx.url", matches="jdbc:informix-sqli://127[.]0[.]0[.]1:19088/interval_test:INFORMIXSERVER=informix.*")
class InformixIntervalServerTest {
	@Test void orderedListsPreserveLargeYearFieldsOrderAndDuplicates() throws Exception {
		Class.forName("com.informix.jdbc.IfxDriver");
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.ifx.url"), "informix", System.getProperty("sqlapp.interval.ifx.password", "interval_test_2026"));
				var sql = connection.createStatement()) {
			var handler = new InformixIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR_TO_MONTH);
			var column = new Column("c").setDataType(DataType.INTERVAL_YEAR_TO_MONTH).setArrayDimension(1)
					.setDataTypeName("LIST(INTERVAL YEAR(9) TO MONTH NOT NULL)");
			String table="i_list_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
			sql.execute("CREATE TABLE "+table+"(c " + column.getDataTypeName() + ")");
			try {
			var dialect=com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
			var reader=dialect.getCatalogReader().getSchemaReader().getTableReader().getColumnReader();
			reader.setSchemaName("informix");reader.setObjectName(table);
			var captured=reader.getAll(connection).getFirst();
			assertEquals(DataType.INTERVAL_YEAR_TO_MONTH,captured.getDataType());assertEquals(1,captured.getArrayDimension());
			var recreated=new com.sqlapp.data.schemas.Table(table+"_copy");recreated.getColumns().add(captured.clone());
			for(var operation:dialect.createSqlFactoryRegistry().createSql(recreated,com.sqlapp.data.db.sql.SqlType.CREATE))sql.execute(operation.getSqlText());
			sql.execute("DROP TABLE "+table+"_copy");
			Object[] values = {IntervalValue.parse("P11999999999M"), IntervalValue.parse("P-1M"), IntervalValue.parse("P11999999999M")};
			try (var insert = connection.prepareStatement("INSERT INTO "+table+" VALUES(?)")) {
				handler.setObject(insert, 1, values, column); insert.executeUpdate();
				handler.setObject(insert, 1, new Object[0], column); insert.executeUpdate();
				handler.setObject(insert, 1, null, column); insert.executeUpdate();
			}
			sql.execute("INSERT INTO "+table+" VALUES(" + handler.literal(values, column) + ")");
			try (var rows = sql.executeQuery("SELECT " + handler.readExpression("c", column) + " FROM "+table)) {
				assertTrue(rows.next()); assertArrayEquals(values, (Object[]) handler.getObject(rows, 1, column));
				assertTrue(rows.next()); assertArrayEquals(new Object[0], (Object[]) handler.getObject(rows, 1, column));
				assertTrue(rows.next()); assertNull(handler.getObject(rows, 1, column));
				assertTrue(rows.next()); assertArrayEquals(values, (Object[]) handler.getObject(rows, 1, column));
			}
			assertThrows(IllegalArgumentException.class, () -> handler.prepareInterval(new Object[]{null}, column));
			} finally {sql.execute("DROP TABLE "+table);}
		}
	}

	@Test void nativeQualifiersBoundsFractionsLiteralsAndNullsRoundTrip() throws Exception {
		Class.forName("com.informix.jdbc.IfxDriver");
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.ifx.url"), "informix", System.getProperty("sqlapp.interval.ifx.password", "interval_test_2026"));
				var sql = connection.createStatement()) {
			String[][] cases = {
				{"INTERVAL YEAR(9) TO MONTH", "P11999999999M"},
				{"INTERVAL MONTH(9) TO MONTH", "P999999999M"},
				{"INTERVAL DAY(9) TO FRACTION(5)", "P999999999DT23H59M59.99999S"},
				{"INTERVAL HOUR(9) TO FRACTION(5)", "PT999999999H59M59.99999S"},
				{"INTERVAL SECOND(9) TO FRACTION(5)", "PT999999999.99999S"},
				{"INTERVAL FRACTION TO FRACTION(5)", "PT0.99999S"}
			};
			for (String[] sample : cases) {
				DataType group = sample[0].contains("YEAR") || sample[0].contains("MONTH") ? DataType.INTERVAL_YEAR_TO_MONTH : DataType.INTERVAL_DAY_TO_SECOND;
				var column = new Column("c").setDataType(group).setDataTypeName(sample[0]);
				var handler = new InformixIntervalJdbcTypeHandler(group);
				String table = "i_s9_" + UUID.randomUUID().toString().replace("-", "").substring(0,10);
				sql.execute("CREATE TABLE " + table + "(id INT,c " + sample[0] + ")");
				try {
					for (var input : new IntervalValue[] {IntervalValue.parse(sample[1]), IntervalValue.parse(sample[1]).negated()}) {
						var expected = handler.prepareInterval(input, column).value();

						sql.execute("DELETE FROM " + table);
						try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES(?,?)")) {
							insert.setInt(1,1); handler.setObject(insert,2,input,column); insert.executeUpdate();
							insert.setInt(1,2); handler.setObject(insert,2,null,column); insert.executeUpdate();
						}
						try (var rows = sql.executeQuery("SELECT CAST(c AS VARCHAR(128)) AS c FROM " + table + " ORDER BY id")) {
							assertTrue(rows.next());
							assertEquals(expected, handler.getObject(rows,1,column), sample[0]);
							assertTrue(rows.next()); assertNull(handler.getObject(rows,1,column));
						}
						try (var rows = sql.executeQuery("SELECT CAST(" + handler.literal(input,column) + " AS VARCHAR(128)) FROM systables WHERE tabid=1")) {
							assertTrue(rows.next()); assertEquals(expected, handler.getObject(rows,1,column));
						}
					}
				} finally { sql.execute("DROP TABLE " + table); }
			}
		}
	}
}
