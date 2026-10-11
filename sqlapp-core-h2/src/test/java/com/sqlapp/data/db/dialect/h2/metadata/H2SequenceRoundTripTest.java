/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class H2SequenceRoundTripTest {
	@Test
	void supportsExplicitNoCacheWithoutASizeAndRejectsNegativeSizes() {
		var registry = com.sqlapp.data.db.dialect.h2.DialectHolder.defaultDialect200.createSqlFactoryRegistry();
		var sequence = new com.sqlapp.data.schemas.Sequence("audit_seq").setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NO CACHE"));
		sequence.setCacheSize(-1);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
	}

	@Test
	void preservesCycleAndBoundsWhenRecreated() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:sequence-audit", "sa", "");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setSchemaName("PUBLIC");
			for (boolean cycle : new boolean[] { true, false }) {
				statement.execute(
						"CREATE SEQUENCE AUDIT_SEQ AS INTEGER START WITH 50 INCREMENT BY 5 MINVALUE 5 MAXVALUE 1000 "
								+ (cycle ? "CYCLE" : "NO CYCLE") + " CACHE 1");
				var sequence = reader.getAll(connection).get(0);
				assertEquals(com.sqlapp.data.db.datatype.DataType.INT, sequence.getDataType());
				assertEquals(cycle, sequence.isCycle());
				assertEquals(5, sequence.getMinValue().intValue());
				assertEquals(1000, sequence.getMaxValue().intValue());
				assertFalse(sequence.isCache());
				var ddl = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
				statement.execute("DROP SEQUENCE AUDIT_SEQ");
				for (var sql : ddl) {
					statement.execute(sql.getSqlText());
				}
				var recreated = reader.getAll(connection).get(0);
				assertEquals(sequence.getDataType(), recreated.getDataType());
				assertEquals(sequence.getStartValue(), recreated.getStartValue());
				assertEquals(sequence.getMinValue(), recreated.getMinValue());
				assertEquals(sequence.getMaxValue(), recreated.getMaxValue());
				assertEquals(sequence.getIncrementBy(), recreated.getIncrementBy());
				assertEquals(cycle, recreated.isCycle());
				assertFalse(recreated.isCache());
				statement.execute("DROP SEQUENCE AUDIT_SEQ");
			}
		}
	}
}
