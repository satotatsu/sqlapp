/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class FirebirdSequenceAlterSqlTest {
	@Test
	void zeroAndNegativeCurrentValuesAreEmitted() {
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertEquals("SET GENERATOR \"quoted sequence\" TO 0",
				registry.createSql(new Sequence("quoted sequence").setLastValue(0), SqlType.ALTER).get(0).getSqlText());
		assertTrue(registry.createSql(new Sequence("s").setLastValue(-7), SqlType.ALTER).get(0).getSqlText()
				.endsWith(" TO -7"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Sequence("s"), SqlType.ALTER));
	}

	@Test
	void commentOnlyDiffDoesNotResetCurrentValue() {
		Sequence original = new Sequence("s").setLastValue(900);
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		var sql = registry.createSql(original.diff(original.clone().setRemarks("日本語")), SqlType.ALTER);
		assertEquals(1, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("COMMENT ON GENERATOR"));
	}

	@Test
	void incrementAndCurrentValueUseSeparateStatements() {
		Sequence original = new Sequence("s").setLastValue(100).setIncrementBy(1);
		Sequence target = original.clone().setLastValue(0).setIncrementBy(-3);
		var sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().endsWith(" INCREMENT BY -3"));
		assertTrue(sql.get(1).getSqlText().endsWith(" TO 0"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void invalidIncrementAndValueAreRejected() {
		Sequence original = new Sequence("s").setLastValue(1).setIncrementBy(1);
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(original.clone().setIncrementBy(0)), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class, () -> registry
				.createSql(original.diff(original.clone().setLastValue((BigInteger) null)), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(
						original.clone().setLastValue(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
						SqlType.ALTER));
	}

	@Test
	void startMetadataAndUnsupportedOptionsAreNotSilentlyIgnored() {
		Sequence original = new Sequence("s").setLastValue(1).setStartValue(1);
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(original.clone().setStartValue(50)), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(original.clone().setCycle(true)), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(original.clone().setName("renamed")), SqlType.ALTER));
	}
}
