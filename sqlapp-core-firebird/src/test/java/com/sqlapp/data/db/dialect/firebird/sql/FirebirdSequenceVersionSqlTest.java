/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class FirebirdSequenceVersionSqlTest {
	@Test
	void isolatesSequenceOptionsToVersionThreeAndLater() {
		var sequence = new Sequence("audit_seq").setStartValue(50).setIncrementBy(5);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE));
		var legacy = DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(sequence.clone().setIncrementBy(1), SqlType.CREATE).get(0).getSqlText();
		assertFalse(legacy.contains("START WITH"));
		assertFalse(legacy.contains("INCREMENT BY"));
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect50 }) {
			var registry = dialect.createSqlFactoryRegistry();
			var sql = registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("START WITH 50"), sql);
			assertTrue(sql.contains("INCREMENT BY 5"), sql);
			assertInstanceOf(Firebird30CreatePackageFactory.class,
					registry.getSqlFactory(new com.sqlapp.data.schemas.Package("audit_package"), SqlType.CREATE));
		}
	}

	@Test
	void recreatesCurrentValueBeforeCommentsAndPreservesZero() {
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect25,
				DialectHolder.defaultDialect30, DialectHolder.defaultDialect50 }) {
			var sequence = new Sequence("quoted sequence").setStartValue(50).setLastValue(0).setRemarks("remark");
			var sql = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
			assertEquals(3, sql.size());
			assertEquals("SET GENERATOR \"quoted sequence\" TO 0", sql.get(1).getSqlText());
			assertTrue(sql.get(2).getSqlText().startsWith("COMMENT ON GENERATOR"));
		}
	}

	@Test
	void legacyStartInitializesCurrentValueWhileModernStartUsesNativeClause() {
		var sequence = new Sequence("s").setStartValue(-10);
		var legacy = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
		assertEquals(2, legacy.size());
		assertTrue(legacy.get(1).getSqlText().endsWith(" TO -10"));
		var modern = DialectHolder.defaultDialect50.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
		assertEquals(1, modern.size());
		assertTrue(modern.get(0).getSqlText().contains("START WITH -10"));
	}

	@Test
	void rejectsUnsupportedOptionsAndInvalidNumericRanges() {
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		for (var sequence : new Sequence[] { new Sequence("s").setIncrementBy(0), new Sequence("s").setCycle(true),
				new Sequence("s").setMinValue(1), new Sequence("s").setMaxValue(10), new Sequence("s").setCacheSize(10),
				new Sequence("s").setOrder(true),
				new Sequence("s").setDataType(com.sqlapp.data.db.datatype.DataType.INT), new Sequence("s")
						.setLastValue(java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE)) })
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
	}

	@Test
	void acceptsIntrinsicBigintWithoutAnAsClause() {
		var sequence = new Sequence("s").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setLastValue(Long.MIN_VALUE);
		var sql = DialectHolder.defaultDialect50.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
		assertFalse(sql.get(0).getSqlText().contains("BIGINT"));
		assertTrue(sql.get(1).getSqlText().endsWith(" TO " + Long.MIN_VALUE));
	}
}
