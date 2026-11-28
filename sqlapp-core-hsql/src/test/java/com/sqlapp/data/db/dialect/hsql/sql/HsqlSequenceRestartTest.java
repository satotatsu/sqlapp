/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.hsql.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Sequence;

class HsqlSequenceRestartTest {
	@Test
	void createAndDifferenceRestartAtTheNextValue() throws Exception {
		var registry = DialectHolder.defaultDialect2_0_0.createSqlFactoryRegistry();
		var sequence = new Sequence("S").setStartValue(BigInteger.ONE).setIncrementBy(BigInteger.valueOf(3))
				.setLastValue(BigInteger.valueOf(40));
		var restarted = sequence.clone().setLastValue(BigInteger.valueOf(700));
		restarted.setCacheSize(50);
		var difference = sequence.diff(restarted, new DefaultSchemaEqualsHandler().setCompareSequenceValues(true));
		var alter = registry.getSqlFactory(difference).createDiffSql(difference);
		assertEquals(1, alter.size(), alter.toString());
		assertFalse(alter.get(0).getSqlText().contains("CACHE"), alter.toString());
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:restart", "SA", "");
				var statement = connection.createStatement()) {
			for (var operation : registry.createSql(sequence, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			assertEquals(40, next(statement));
			assertEquals(43, next(statement));
			for (var operation : alter)
				statement.execute(operation.getSqlText());
			assertEquals(700, next(statement));
			statement.execute("DROP SEQUENCE S");
		}
	}

	private static long next(java.sql.Statement statement) throws Exception {
		try (var rows = statement.executeQuery("VALUES NEXT VALUE FOR S")) {
			rows.next();
			return rows.getLong(1);
		}
	}
}
