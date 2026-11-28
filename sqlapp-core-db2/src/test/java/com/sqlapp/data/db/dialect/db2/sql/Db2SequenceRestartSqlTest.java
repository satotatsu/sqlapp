/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Sequence;

/**
 * Db2 never restarts a sequence automatically; the next value is not a DDL
 * setting.
 */
class Db2SequenceRestartSqlTest {
	@Test
	void createDoesNotRestart() {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		var sequence = new Sequence("S").setStartValue(BigInteger.ONE).setLastValue(BigInteger.valueOf(42));
		var sql = registry.createSql(sequence, SqlType.CREATE);
		assertEquals(1, sql.size(), sql.toString());
		assertFalse(sql.get(0).getSqlText().contains("RESTART"), sql.toString());
	}

	@Test
	void settingChangesIgnoreDriftingNextValues() {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		var original = new Sequence("S").setStartValue(BigInteger.ONE).setIncrementBy(BigInteger.ONE)
				.setLastValue(BigInteger.valueOf(42));
		var target = original.clone().setIncrementBy(BigInteger.TWO).setLastValue(BigInteger.valueOf(90));
		var difference = original.diff(target, new DefaultSchemaEqualsHandler());
		var sql = registry.getSqlFactory(difference).createDiffSql(difference).toString();
		assertTrue(sql.contains("INCREMENT BY 2"), sql);
		assertFalse(sql.contains("RESTART"), sql);
	}

	@Test
	void fullComparisonOfTheNextValueFails() {
		var registry = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry();
		var original = new Sequence("S").setStartValue(BigInteger.ONE).setLastValue(BigInteger.valueOf(42));
		var target = original.clone().setLastValue(BigInteger.valueOf(90));
		var difference = original.diff(target, new DefaultSchemaEqualsHandler().setCompareSequenceValues(true));
		var factory = registry.getSqlFactory(difference);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(difference));
	}
}
