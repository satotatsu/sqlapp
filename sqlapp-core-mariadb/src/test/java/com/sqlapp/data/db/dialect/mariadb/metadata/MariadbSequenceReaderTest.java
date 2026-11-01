/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbSequenceReaderTest {
	private class Reader extends MariadbSequence11_50Reader {
		BigDecimal start = BigDecimal.TEN;
		int queries;
		Reader() { super(DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0)); }
		@Override protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try { h.handleResultSetNext(new ExResultSet(null) {
				@Override public String getString(String name) {
					return switch (name.toUpperCase()) {
					case "SEQUENCE_NAME" -> "seq";
					case "SEQUENCE_SCHEMA" -> "app";
					case "SEQUENCE_CATALOG" -> "def";
					case "DATA_TYPE" -> "bigint";
					default -> null;
					};
				}
				@Override public Integer getInteger(String name) { return name.equals("NUMERIC_SCALE") ? 0 : 64; }
				@Override public int getInt(String name) { return 0; }
				@Override public BigDecimal getBigDecimal(String name) {
					return switch (name) {
					case "START_VALUE" -> start;
					case "MINIMUM_VALUE", "INCREMENT" -> BigDecimal.ONE;
					case "MAXIMUM_VALUE" -> BigDecimal.valueOf(Long.MAX_VALUE - 1);
					default -> null;
					};
				}
			}); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
			return null;
		}
	}

	@Test
	void retainsSequenceTypeThroughXmlAndHonorsExistenceOption() throws Exception {
		final var reader = new Reader();
		final var sequence = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(1, reader.queries);
		assertEquals(DataType.BIGINT, sequence.getDataType());
		final Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
		assertEquals(DataType.BIGINT, restored.getDataType());
		final var registry = DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0).createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		assertFalse(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
		registry.getOptions().setCreateIfNotExists(true);
		assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
	}

	@Test
	void doesNotSilentlyTruncateFractionalSequenceMetadata() {
		final var reader = new Reader(); reader.start = new BigDecimal("1.5");
		assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
