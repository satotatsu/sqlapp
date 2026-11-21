/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Sequence;

class PostgresSequenceRestartSqlTest {
	@Test
	void createKeepsStartAndRestartsAtTheNextValue() {
		var registry = DialectHolder.postgreSQL180.createSqlFactoryRegistry();
		var sequence = new Sequence("s").setStartValue(BigInteger.ONE).setLastValue(BigInteger.valueOf(42));
		var sql = registry.createSql(sequence, SqlType.CREATE);
		assertTrue(sql.get(0).getSqlText().contains("START WITH 1"), sql.toString());
		assertEquals("ALTER SEQUENCE s RESTART WITH 42", sql.get(sql.size() - 1).getSqlText());
	}

	@Test
	void differenceUsesPostgresSpelling() {
		var registry = DialectHolder.postgreSQL180.createSqlFactoryRegistry();
		var original = new Sequence("s").setMaxValue(BigInteger.TEN);
		original.setCacheSize(20);
		var target = original.clone().setMaxValue((java.math.BigInteger) null).setLastValue(BigInteger.valueOf(9));
		target.setCacheSize((Integer) null);
		target.setRemarks(null);
		original.setRemarks("old");
		var difference = original.diff(target, new DefaultSchemaEqualsHandler().setCompareSequenceValues(true));
		var sql = registry.getSqlFactory(difference).createDiffSql(difference);
		assertEquals("ALTER SEQUENCE s NO MAXVALUE CACHE 1 RESTART WITH 9", sql.get(0).getSqlText());
		assertEquals("COMMENT ON SEQUENCE s IS NULL", sql.get(1).getSqlText());
	}
}
