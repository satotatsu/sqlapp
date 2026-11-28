/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Sequence;

class OracleSequenceRestartSqlTest {
	@Test
	void createStartsAtTheNextValueOnEveryVersion() {
		var sequence = new Sequence("S").setStartValue(BigInteger.ONE).setLastValue(BigInteger.valueOf(42));
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.oracle12cDialect,
				DialectHolder.oracle18cDialect }) {
			var sql = dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE);
			assertEquals(1, sql.size(), sql.toString());
			assertTrue(sql.get(0).getSqlText().contains("START WITH 42"), sql.toString());
		}
	}

	@Test
	void restartRequires18c() {
		var original = new Sequence("S").setStartValue(BigInteger.ONE);
		var target = original.clone().setLastValue(BigInteger.valueOf(42)).setMinValue((java.math.BigInteger) null)
				.setCycle(false);
		var handler = new DefaultSchemaEqualsHandler().setCompareSequenceValues(true);
		var difference = original.diff(target, handler);
		var sql = DialectHolder.oracle18cDialect.createSqlFactoryRegistry().getSqlFactory(difference)
				.createDiffSql(difference);
		assertEquals("ALTER SEQUENCE S RESTART START WITH 42", sql.get(0).getSqlText());
		var legacy = DialectHolder.oracle12cDialect.createSqlFactoryRegistry().getSqlFactory(difference);
		assertThrows(IllegalArgumentException.class, () -> legacy.createDiffSql(difference));
	}

	@Test
	void differenceUsesOracleSpelling() {
		var original = new Sequence("S").setMaxValue(BigInteger.TEN).setCycle(true);
		original.setCacheSize(20);
		var target = original.clone().setMaxValue((java.math.BigInteger) null).setCycle(false);
		target.setCacheSize((Integer) null);
		var difference = original.diff(target, new DefaultSchemaEqualsHandler());
		var sql = DialectHolder.oracle12cDialect.createSqlFactoryRegistry().getSqlFactory(difference)
				.createDiffSql(difference);
		assertEquals("ALTER SEQUENCE S NOMAXVALUE NOCYCLE NOCACHE", sql.get(0).getSqlText());
	}
}
