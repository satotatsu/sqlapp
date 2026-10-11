/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;

/** Shared MySQL/MariaDB table-scoped index identity regression. */
public final class MySqlIndexAuditAssertions {
	private MySqlIndexAuditAssertions() {
	}

	public static void verify(Connection connection) throws SQLException {
		String prefix = "index_audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String a = prefix + "_a";
		String b = prefix + "_b";
		try (var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE TABLE " + a
						+ " (id INT, value VARCHAR(40), INDEX shared_index(id, value(7)) COMMENT 'first table index')");
				statement.execute("CREATE TABLE " + b
						+ " (id INT, value VARCHAR(40), INDEX shared_index(value(9)) COMMENT 'second table index')");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(connection.getCatalog());

				var count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection,
						sql -> sql.toLowerCase().contains("information_schema.statistics"), count);
				var indexes = reader.getAllFull(counted).stream()
						.filter(t -> a.equals(t.getName()) || b.equals(t.getName()))
						.flatMap(t -> t.getIndexes().stream()).filter(i -> "shared_index".equals(i.getName())).toList();
				assertEquals(1, count.get());
				assertEquals(2, indexes.size());
				var first = indexes.stream().filter(i -> a.equals(i.getTableName())).findFirst().orElseThrow();
				var second = indexes.stream().filter(i -> b.equals(i.getTableName())).findFirst().orElseThrow();
				assertEquals("first table index", first.getRemarks());
				assertEquals("second table index", second.getRemarks());
				assertEquals(2, first.getColumns().size());
				assertEquals("id", first.getColumns().get(0).getName());
				assertEquals(7L, first.getColumns().get(1).getLength());
				assertEquals(1, second.getColumns().size());
				assertEquals("value", second.getColumns().get(0).getName());
				assertEquals(9L, second.getColumns().get(0).getLength());
			} finally {
				statement.execute("DROP TABLE IF EXISTS " + b);
				statement.execute("DROP TABLE IF EXISTS " + a);
			}
		}
	}
}
