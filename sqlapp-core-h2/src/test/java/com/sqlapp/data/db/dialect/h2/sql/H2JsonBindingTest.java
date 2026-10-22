/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class H2JsonBindingTest {
	static Stream<Arguments> cases() {
		return Stream.of(SqlType.INSERT, SqlType.INSERT_ROWS, SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.UPDATE)
				.flatMap(type -> Stream.of("{\"name\":\"日本語\",\"n\":2}", "[1,2]", "\"text\"", "null", "SQL_NULL")
						.map(value -> Arguments.of(type, value)));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void bindsJsonDocumentsWithoutDoubleEncodingAndDistinguishesSqlNull(SqlType type, String value) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY,C JSON)");
			if (type == SqlType.UPDATE || type == SqlType.MERGE || type == SqlType.MERGE_ROWS)
				statement.execute("INSERT INTO T VALUES(1,JSON '{}')");
			var table = H2NativeDmlTest.read(connection, "PUBLIC", "T");
			var row = table.getRows().newElement();
			row.put("ID", 1);
			row.put("C", value.equals("SQL_NULL") ? null : value);
			table.getRows().add(row);
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			assertEquals(1, H2NativeDmlTest.write(connection, registry, table, type,
					type == SqlType.INSERT_ROWS || type == SqlType.MERGE_ROWS ? table : row));
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				assertTrue(rows.next());
				assertEquals(value.equals("SQL_NULL") ? null : value, rows.getString(1));
			}
		}
	}
}
