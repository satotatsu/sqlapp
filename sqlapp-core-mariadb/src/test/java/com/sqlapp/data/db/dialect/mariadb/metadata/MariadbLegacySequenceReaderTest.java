/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MariadbLegacySequenceReaderTest {
	private static class Reader extends MariadbSequence10_30Reader {
		int count = 205;
		int queries;
		boolean missing;
		boolean duplicate;
		Integer rowId;
		BigDecimal cache = BigDecimal.valueOf(50);
		BigDecimal start = BigDecimal.ONE;
		List<String> sqls = new ArrayList<>();

		Reader() {
			super(DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			String sql = n.eval(p).getSql();
			if (queries > 1)
				assertNull(n.getRowParameter());
			sqls.add(sql);
			try {
				int size = queries == 1 ? count : sql.split(" UNION ALL ").length;
				for (int i = 0; i < size && !(missing && queries > 1); i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name) {
							case "TABLE_CATALOG" -> "def";
							case "TABLE_SCHEMA" -> "app`archive";
							case "TABLE_NAME" -> "seq`" + id;
							case "ENGINE" -> "InnoDB";
							case "TABLE_COMMENT" -> "owner's sequence";
							default -> null;
							};
						}

						@Override
						public int getInt(String name) {
							return name.equals("sequence_id") ? (rowId == null ? (duplicate ? 0 : id) : rowId) : 1;
						}

						@Override
						public BigDecimal getBigDecimal(String name) {
							return switch (name) {
							case "maximum_value" -> BigDecimal.valueOf(9999);
							case "cache_size" -> cache;
							case "start_value" -> start;
							default -> BigDecimal.ONE;
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
	void reads205SequencesInFourQueriesAndRecreatesFromXml() throws Exception {
		var reader = new Reader();
		var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(205, sequences.size());
		assertEquals(4, reader.queries);
		assertTrue(reader.sqls.get(1).contains("`app``archive`.`seq``0`"));
		Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequences.get(204).asXml()));
		assertEquals(50, restored.getCacheSize());
		assertEquals("InnoDB", restored.getSpecifics().get("ENGINE"));
		assertEquals("owner's sequence", restored.getRemarks());
		assertTrue(restored.isCycle());
		String ddl = DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0).createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(ddl.contains("CACHE 50"), ddl);
		assertTrue(ddl.contains("ENGINE =InnoDB"), ddl);
		assertTrue(ddl.contains("owner''s sequence"), ddl);
		assertFalse(ddl.contains(" AS BIGINT"), ddl);
	}

	@Test
	void emptyDiscoveryDoesNotQueryStorageAndMissingRowsFail() {
		var empty = new Reader();
		empty.count = 0;
		assertTrue(empty.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(1, empty.queries);
		var missing = new Reader();
		missing.count = 1;
		missing.missing = true;
		assertThrows(IllegalStateException.class, () -> missing.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void batchBoundariesKeepQueryCountBounded() {
		for (int count : new int[] { 1, 99, 100, 101, 200, 201 }) {
			var reader = new Reader();
			reader.count = count;
			assertEquals(count, reader.doGetAll(null, new ParametersContext(), null).size());
			assertEquals(1 + (count + 99) / 100, reader.queries);
		}
	}

	@Test
	void malformedBatchIdsAndDuplicateRowsFailExplicitly() {
		for (int id : new int[] { -1, 1, Integer.MAX_VALUE }) {
			var reader = new Reader();
			reader.count = 1;
			reader.rowId = id;
			assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
		var duplicate = new Reader();
		duplicate.count = 2;
		duplicate.duplicate = true;
		assertThrows(IllegalStateException.class, () -> duplicate.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void numericMetadataIsNeverSilentlyRoundedAndZeroCacheIsRetained() {
		for (String size : new String[] { "2147483648", "1.5" }) {
			var reader = new Reader();
			reader.count = 1;
			reader.cache = new BigDecimal(size);
			assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		}
		var reader = new Reader();
		reader.count = 1;
		reader.cache = BigDecimal.valueOf(-1);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		reader.cache = BigDecimal.ZERO;
		Sequence sequence = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertFalse(sequence.isCache());
		assertEquals(0, sequence.getCacheSize());
		reader.start = new BigDecimal("1.5");
		assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void schemaReaderKeepsVersionBoundaries() {
		assertNull(new MariadbSchema10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 7))
				.newSequenceReader());
		assertInstanceOf(MariadbSequence10_30Reader.class,
				new MariadbSchema10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0))
						.newSequenceReader());
		assertInstanceOf(MariadbSequence11_50Reader.class,
				new MariadbSchema11_50Reader(DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0))
						.newSequenceReader());
	}
}
