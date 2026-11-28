/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;

class H2SequenceRestartTest {
	private static final com.sqlapp.data.db.dialect.Dialect DIALECT = DialectResolver.getInstance().getDialect("H2", 2,
			3, 232);

	@Test
	void createStartsAtTheNextValueAndKeepsTheStartValue() throws Exception {
		var sequence = new Sequence("S").setStartValue(BigInteger.ONE).setIncrementBy(BigInteger.valueOf(5))
				.setLastValue(BigInteger.valueOf(101));
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:")) {
			execute(connection, DIALECT.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE));
			assertEquals(101, next(connection));
			assertEquals(106, next(connection));
			try (var rows = connection.createStatement()
					.executeQuery("SELECT START_VALUE FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME='S'")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getLong(1));
			}
		}
	}

	@Test
	void noRestartWhenTheNextValueIsTheStartValueOrUnknown() {
		var registry = DIALECT.createSqlFactoryRegistry();
		assertEquals(1, registry
				.createSql(new Sequence("S").setStartValue(BigInteger.TEN).setLastValue(BigInteger.TEN), SqlType.CREATE)
				.size());
		assertEquals(1, registry.createSql(new Sequence("S"), SqlType.CREATE).size());
	}

	@Test
	void fullComparisonRestartsAndAltersInPlace() throws Exception {
		var original = new Sequence("S").setStartValue(BigInteger.ONE).setIncrementBy(BigInteger.ONE);
		original.setSchemaName("PUBLIC");
		var target = original.clone().setLastValue(BigInteger.valueOf(500)).setIncrementBy(BigInteger.TEN)
				.setMaxValue(BigInteger.valueOf(100000)).setCycle(true).setRemarks("ids");
		var registry = DIALECT.createSqlFactoryRegistry();
		var handler = new DefaultSchemaEqualsHandler().setCompareSequenceValues(true);
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:")) {
			execute(connection, registry.createSql(original, SqlType.CREATE));
			var operations = diffSql(registry, original, target, handler);
			assertEquals(2, operations.size(), operations.toString());
			assertTrue(operations.get(0).getSqlText().contains("RESTART WITH 500"), operations.toString());
			execute(connection, operations);
			assertEquals(500, next(connection));
			assertEquals(510, next(connection));
			try (var rows = connection.createStatement().executeQuery(
					"SELECT MAXIMUM_VALUE, CYCLE_OPTION, REMARKS FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME='S'")) {
				assertTrue(rows.next());
				assertEquals(100000, rows.getLong(1));
				assertEquals("YES", rows.getString(2));
				assertEquals("ids", rows.getString(3));
			}
		}
	}

	@Test
	void defaultComparisonIgnoresTheNextValue() {
		var original = new Sequence("S").setStartValue(BigInteger.ONE);
		var target = original.clone().setLastValue(BigInteger.valueOf(500));
		var registry = DIALECT.createSqlFactoryRegistry();
		assertTrue(diffSql(registry, original, target, new DefaultSchemaEqualsHandler()).isEmpty());
	}

	@Test
	void schemaDifferenceAltersModifiedSequences() {
		var before = new Schema("PUBLIC");
		before.getSequences().add(new Sequence("S").setIncrementBy(BigInteger.ONE));
		var after = new Schema("PUBLIC");
		after.getSequences().add(new Sequence("S").setIncrementBy(BigInteger.TWO));
		var registry = DIALECT.createSqlFactoryRegistry();
		var difference = before.diff(after, new DefaultSchemaEqualsHandler());
		var sql = registry.getSqlFactory(difference).createDiffSql(difference).toString();
		assertTrue(sql.contains("INCREMENT BY 2"), sql);
	}

	@Test
	void unsupportedChangesFailInsteadOfBeingDropped() {
		var original = new Sequence("S").setDataType(DataType.INT);
		var target = original.clone().setDataType(DataType.BIGINT);
		var registry = DIALECT.createSqlFactoryRegistry();
		var error = assertThrows(IllegalArgumentException.class,
				() -> diffSql(registry, original, target, new DefaultSchemaEqualsHandler()));
		assertTrue(error.getMessage().contains("dataType"), error.getMessage());
		var cleared = new Sequence("S").setIncrementBy(BigInteger.TWO);
		assertThrows(IllegalArgumentException.class,
				() -> diffSql(registry, cleared, new Sequence("S"), new DefaultSchemaEqualsHandler()));
	}

	@Test
	void boundsRequireH2_20() {
		var legacy = DialectResolver.getInstance().getDialect("H2", 1, 4, 200).createSqlFactoryRegistry();
		var original = new Sequence("S");
		assertThrows(IllegalArgumentException.class, () -> diffSql(legacy, original,
				original.clone().setMaxValue(BigInteger.TEN), new DefaultSchemaEqualsHandler()));
		assertTrue(diffSql(legacy, original, original.clone().setLastValue(BigInteger.TEN),
				new DefaultSchemaEqualsHandler().setCompareSequenceValues(true)).get(0).getSqlText()
				.contains("RESTART WITH 10"));
	}

	static java.util.List<com.sqlapp.data.db.sql.SqlOperation> diffSql(SqlFactoryRegistry registry, Sequence original,
			Sequence target, DefaultSchemaEqualsHandler handler) {
		var difference = original.diff(target, handler);
		return registry.getSqlFactory(difference).createDiffSql(difference);
	}

	private static void execute(Connection connection, java.util.List<com.sqlapp.data.db.sql.SqlOperation> operations)
			throws Exception {
		try (var statement = connection.createStatement()) {
			for (var operation : operations)
				statement.execute(operation.getSqlText());
		}
	}

	private static long next(Connection connection) throws Exception {
		try (var rows = connection.createStatement().executeQuery("SELECT NEXT VALUE FOR S")) {
			rows.next();
			return rows.getLong(1);
		}
	}
}
