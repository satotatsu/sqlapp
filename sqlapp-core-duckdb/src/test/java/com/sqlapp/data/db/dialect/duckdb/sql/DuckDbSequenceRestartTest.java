/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Sequence;

class DuckDbSequenceRestartTest {
	@Test
	void createStartsAtTheNextValue() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var sequence = new Sequence("s").setStartValue(BigInteger.ONE).setIncrementBy(BigInteger.TWO)
				.setLastValue(BigInteger.valueOf(31));
		var sql = registry.createSql(sequence, SqlType.CREATE);
		assertEquals(1, sql.size(), sql.toString());
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			statement.execute(sql.get(0).getSqlText());
			try (var rows = statement.executeQuery("SELECT nextval('s'), nextval('s')")) {
				assertTrue(rows.next());
				assertEquals(31, rows.getLong(1));
				assertEquals(33, rows.getLong(2));
			}
		}
	}

	@Test
	void modifiedSequencesFailExplicitly() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var original = new Sequence("s").setIncrementBy(BigInteger.ONE);
		var target = original.clone().setIncrementBy(BigInteger.TWO);
		var difference = original.diff(target, new DefaultSchemaEqualsHandler());
		var factory = registry.getSqlFactory(difference);
		var error = assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(difference));
		assertTrue(error.getMessage().contains("incrementBy"), error.getMessage());
	}
}
