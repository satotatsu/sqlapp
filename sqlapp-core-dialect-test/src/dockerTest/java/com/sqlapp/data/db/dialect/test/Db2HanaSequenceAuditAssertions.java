/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.SchemaUtils;

/** Local disposable database sequence XML/DDL round trips. */
public final class Db2HanaSequenceAuditAssertions {
	private Db2HanaSequenceAuditAssertions() {
	}

	public static void verify(Connection connection, boolean db2) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String owner = db2 ? connection.getMetaData().getUserName().toUpperCase(Locale.ROOT) : "METADATA_TEST";
		String prefix = "SQ_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
		var existing = new java.util.LinkedHashSet<String>();
		try (var statement = connection.createStatement()) {
			try {
				for (int i = 0; i < 3; i++) {
					statement.execute(
							"CREATE SEQUENCE " + owner + "." + prefix + i + (db2 && i == 2 ? " AS DECIMAL(20,0)" : "")
									+ " START WITH 50 INCREMENT BY 3 MINVALUE 1 MAXVALUE 1000 CYCLE"
									+ (i == 0 ? " CACHE 7" : " NO CACHE") + (db2 ? " ORDER" : ""));
					existing.add(prefix + i);
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
				reader.setSchemaName(owner);
				var count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection, text -> true, count);
				var sequences = reader.getAllFull(counted).stream().filter(s -> s.getName().startsWith(prefix))
						.toList();
				assertEquals(3, sequences.size());
				assertEquals(1, count.get());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var original : sequences) {
					assertEquals(original.getName().endsWith("0"), original.isCache(),
							"cache size=" + original.getCacheSize());
					assertEquals(original.getName().endsWith("0") ? 7 : 1, original.getCacheSize());
					assertEquals(db2, original.isOrder());
					Sequence restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
					statement.execute("DROP SEQUENCE " + owner + "." + original.getName());
					existing.remove(original.getName());
					for (var operation : registry.createSql(restored, SqlType.CREATE)) {
						try {
							statement.execute(operation.getSqlText());
						} catch (java.sql.SQLException e) {
							throw new AssertionError(operation.getSqlText(), e);
						}
					}
					existing.add(original.getName());
					reader.setObjectName(original.getName());
					count.set(0);
					var after = reader.getAllFull(counted).get(0);
					assertEquals(1, count.get());
					assertEquals(original.getStartValue(), after.getStartValue());
					assertEquals(original.getCacheSize(), after.getCacheSize());
					assertEquals(original.isCache(), after.isCache());
					assertEquals(original.isOrder(), after.isOrder());
					assertEquals(original.getIncrementBy(), after.getIncrementBy());
					assertEquals(original.getMinValue(), after.getMinValue());
					assertEquals(original.getMaxValue(), after.getMaxValue());
					assertEquals(original.isCycle(), after.isCycle());
				}
			} finally {
				for (String name : existing)
					statement.execute("DROP SEQUENCE " + owner + "." + name);
			}
		}
	}
}
