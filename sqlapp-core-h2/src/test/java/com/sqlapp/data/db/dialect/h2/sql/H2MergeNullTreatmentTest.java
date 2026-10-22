/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.h2.sql.H2NativeDmlTest.*;
import java.sql.DriverManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class H2MergeNullTreatmentTest {
	static java.util.stream.Stream<Arguments> cases() {
		return java.util.stream.Stream.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)
				.flatMap(
						type -> java.util.stream.Stream.of("DISTINCT", "NOT DISTINCT", "ALL DISTINCT")
								.flatMap(clause -> java.util.stream.Stream.of("1", "NULL")
										.map(second -> Arguments.of(type, clause, second,
												clause.equals("NOT DISTINCT")
														|| clause.equals("ALL DISTINCT") && second.equals("1") ? 1
																: 2))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void matchesNativeNullableUniqueKeys(SqlType type, String clause, String second, int count) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(A INT,B INT,V INT,CONSTRAINT U UNIQUE NULLS " + clause + "(A,B))");
			statement.execute("INSERT INTO T VALUES(NULL," + second + ",1)");
			var table = read(connection, "PUBLIC", "T");
			var row = table.getRows().newElement();
			row.put("A", null);
			row.put("B", second.equals("NULL") ? null : Integer.valueOf(second));
			row.put("V", 2);
			table.getRows().add(row);
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			if (type == SqlType.MERGE_TABLE) {
				String staging = registry.getTableOptions().getTempTableName().apply(table);
				statement.execute("CREATE TABLE " + quote(staging) + "(A INT,B INT,V INT)");
				statement.execute("INSERT INTO " + quote(staging) + " VALUES(NULL," + second + ",2)");
				for (var operation : registry.createSql(table, type)) {
					statement.execute(operation.getSqlText());
				}
			} else {
				write(connection, registry, table, type, type == SqlType.MERGE ? row : table.getRows());
			}
			try (var rows = statement.executeQuery("SELECT COUNT(*),MAX(V) FROM T")) {
				assertTrue(rows.next());
				assertEquals(count, rows.getInt(1));
				assertEquals(2, rows.getInt(2));
			}
		}
	}
}
