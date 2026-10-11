/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * XML/DDL/reread assertions for sequence configuration, not migration
 * high-water marks.
 */
public final class SequenceMetadataAuditAssertions {
	private SequenceMetadataAuditAssertions() {
	}

	public static void verify(Connection connection, boolean oracle) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String owner = oracle ? connection.getMetaData().getUserName().toUpperCase(Locale.ROOT) : "dbo";
		String prefix = "SA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
		java.util.Set<String> existing = new java.util.LinkedHashSet<>();
		try (var sql = connection.createStatement()) {
			try {
				for (int i = 0; i < 3; i++) {
					String type = oracle ? "" : i == 0 ? " AS decimal(20,0)" : " AS int";
					String cache = i == 0 ? " CACHE 7" : oracle ? " NOCACHE" : " NO CACHE";
					sql.execute("CREATE SEQUENCE " + owner + "." + prefix + i + type
							+ " START WITH 10 INCREMENT BY 3 MINVALUE 1 MAXVALUE 1000 CYCLE" + cache
							+ (oracle ? " ORDER" : ""));
					existing.add(prefix + i);
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
				reader.setSchemaName(owner);
				AtomicInteger count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection, text -> true, count);
				reader.setObjectName(prefix + "0");
				assertEquals(1, reader.getAllFull(counted).size());
				assertEquals(1, count.get());
				count.set(0);
				reader.setObjectName(null);
				var sequences = reader.getAllFull(counted).stream().filter(s -> s.getName().startsWith(prefix))
						.toList();
				assertEquals(3, sequences.size());
				assertEquals(1, count.get());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var original : sequences) {
					Sequence restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
					sql.execute("DROP SEQUENCE " + owner + "." + original.getName());
					existing.remove(original.getName());
					for (var operation : registry.createSql(restored, SqlType.CREATE)) {
						try {
							sql.execute(operation.getSqlText());
						} catch (java.sql.SQLException e) {
							throw new AssertionError(operation.getSqlText(), e);
						}
					}
					existing.add(original.getName());
					reader.setObjectName(original.getName());
					var after = reader.getAllFull(connection).get(0);
					assertEquals(original.isCache(), after.isCache());
					assertEquals(original.getCacheSize(), after.getCacheSize());
					assertEquals(original.isOrder(), after.isOrder());
					assertEquals(original.isCycle(), after.isCycle());
					assertEquals(original.getMinValue(), after.getMinValue());
					assertEquals(original.getMaxValue(), after.getMaxValue());
					assertEquals(original.getIncrementBy(), after.getIncrementBy());
					if (!oracle) {
						assertEquals(original.getDataTypeName(), after.getDataTypeName());
						assertEquals(original.getPrecision(), after.getPrecision());
						assertEquals(original.getStartValue(), after.getStartValue());
					}
				}
			} finally {
				for (String name : existing)
					sql.execute("DROP SEQUENCE " + owner + "." + name);
			}
		}
	}
}
