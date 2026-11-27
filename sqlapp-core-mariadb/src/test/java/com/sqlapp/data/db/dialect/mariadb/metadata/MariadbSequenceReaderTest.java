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
		BigDecimal storageMax = BigDecimal.valueOf(Long.MAX_VALUE - 1);
		int queries;
		int count = 1;
		BigDecimal cache = BigDecimal.valueOf(50);
		boolean missingCache;
		String nativeType = "bigint";
		Integer cacheRowId;

		Reader() {
			super(DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			String sql = n.eval(p).getSql();
			boolean header = queries == 1;
			if (header) {
				assertTrue(sql.contains("LEFT JOIN information_schema.TABLES"), sql);
				assertTrue(sql.contains("t.TABLE_SCHEMA = s.SEQUENCE_SCHEMA"), sql);
				assertTrue(sql.contains("t.TABLE_NAME = s.SEQUENCE_NAME"), sql);
			} else {
				assertNull(n.getRowParameter());
				assertTrue(sql.contains("cache_size FROM `app`.`seq``"), sql);
			}
			try {
				int rows = header ? count : sql.split(" UNION ALL ").length;
				for (int i = 0; i < rows && !(missingCache && !header); i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase()) {
							case "SEQUENCE_NAME" -> "seq`" + id;
							case "SEQUENCE_SCHEMA" -> "app";
							case "SEQUENCE_CATALOG" -> "def";
							case "DATA_TYPE" -> nativeType;
							case "ENGINE" -> "InnoDB";
							case "TABLE_COMMENT" -> "owner's sequence";
							default -> null;
							};
						}

						@Override
						public Integer getInteger(String name) {
							return name.equals("NUMERIC_SCALE") ? 0 : 64;
						}

						@Override
						public Object getObject(String name) {
							return getInteger(name);
						}

						@Override
						public int getInt(String name) {
							return name.equals("sequence_id") ? (cacheRowId == null ? id : cacheRowId) : 0;
						}

						@Override
						public BigDecimal getBigDecimal(String name) {
							return switch (name) {
							case "START_VALUE", "start_value" -> start;
							case "MINIMUM_VALUE", "INCREMENT", "minimum_value", "increment" -> BigDecimal.ONE;
							case "MAXIMUM_VALUE" -> storageMax;
							case "maximum_value" -> storageMax;
							case "cache_size" -> cache;
							default -> null;
							};
						}
					});
				}
			} catch (java.sql.SQLException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}
	}

	@Test
	void retainsSequenceTypeThroughXmlAndHonorsExistenceOption() throws Exception {
		final var reader = new Reader();
		final var sequence = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(2, reader.queries);
		assertEquals(DataType.BIGINT, sequence.getDataType());
		final Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequence.asXml()));
		assertEquals(DataType.BIGINT, restored.getDataType());
		assertEquals("InnoDB", restored.getSpecifics().get("ENGINE"));
		assertEquals("owner's sequence", restored.getRemarks());
		assertEquals(50, restored.getCacheSize());
		final var registry = DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0).createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		String ddl = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(ddl.contains("ENGINE =InnoDB"), ddl);
		assertTrue(ddl.contains("owner''s sequence"), ddl);
		assertTrue(ddl.contains("CACHE 50"), ddl);
		assertFalse(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
		registry.getOptions().setCreateIfNotExists(true);
		assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
	}

	@Test
	void cacheReadsAreBatchedAndRetainUnsignedType() {
		var reader = new Reader();
		reader.count = 205;
		reader.nativeType = "bigint unsigned";
		var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(4, reader.queries);
		assertEquals(205, sequences.size());
		assertTrue(sequences.stream().allMatch(s -> s.getDataType() == DataType.UBIGINT && s.getCacheSize() == 50));
	}

	@Test
	void emptyCacheZeroAndInvalidStorageValuesAreHandledExplicitly() {
		var empty = new Reader();
		empty.count = 0;
		assertTrue(empty.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, empty.queries);
		var zero = new Reader();
		zero.cache = BigDecimal.ZERO;
		assertFalse(zero.doGetAll(null, new ParametersContext(), null).get(0).isCache());
		for (String value : new String[] { "1.5", "2147483648" }) {
			var reader = new Reader();
			reader.cache = new BigDecimal(value);
			assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
		var missing = new Reader();
		missing.missingCache = true;
		assertThrows(IllegalStateException.class, () -> missing.doGetAll(null, new ParametersContext(), null));
		var negative = new Reader();
		negative.cache = BigDecimal.valueOf(-1);
		assertThrows(IllegalStateException.class, () -> negative.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void invalidAndDuplicateCacheRowIdsFail() {
		for (int id : new int[] { -1, 1 }) {
			var reader = new Reader();
			reader.cacheRowId = id;
			assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
		var duplicate = new Reader();
		duplicate.count = 2;
		duplicate.cacheRowId = 0;
		assertThrows(IllegalStateException.class, () -> duplicate.doGetAll(null, new ParametersContext(), null));
		var missing = new Reader();
		missing.cache = null;
		assertThrows(IllegalStateException.class, () -> missing.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void unsignedRangeIsPreservedWithoutClamping() {
		var reader = new Reader();
		reader.nativeType = "bigint unsigned";
		reader.start = new BigDecimal("9223372036854775808");
		reader.storageMax = new BigDecimal("18446744073709551614");
		var sequence = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(DataType.UBIGINT, sequence.getDataType());
		assertEquals(reader.storageMax.toBigIntegerExact(), sequence.getMaxValue());
	}

	@Test
	void doesNotSilentlyTruncateFractionalSequenceMetadata() {
		final var reader = new Reader();
		reader.start = new BigDecimal("1.5");
		assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
