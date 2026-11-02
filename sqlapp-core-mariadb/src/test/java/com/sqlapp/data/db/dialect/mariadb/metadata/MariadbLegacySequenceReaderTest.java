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
		List<String> sqls = new ArrayList<>();
		Reader() { super(DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0)); }
		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			String sql = n.eval(p).getSql();
			sqls.add(sql);
			try {
				int size = queries == 1 ? count : sql.split(" UNION ALL ").length;
				for (int i = 0; i < size && !(missing && queries > 1); i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override public String getString(String name) {
							return switch (name) {
							case "TABLE_CATALOG" -> "def";
							case "TABLE_SCHEMA" -> "app`archive";
							case "TABLE_NAME" -> "seq`" + id;
							default -> null;
							};
						}
						@Override public int getInt(String name) { return name.equals("sequence_id") ? id : 1; }
						@Override public BigDecimal getBigDecimal(String name) {
							return switch (name) {
							case "maximum_value" -> BigDecimal.valueOf(9999);
							case "cache_size" -> BigDecimal.valueOf(50);
							default -> BigDecimal.ONE;
							};
						}
					});
				}
			} catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
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
		assertTrue(restored.isCycle());
		String ddl = DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0).createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(ddl.contains("CACHE 50"), ddl);
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
	void schemaReaderKeepsVersionBoundaries() {
		assertNull(new MariadbSchema10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 7)).newSequenceReader());
		assertInstanceOf(MariadbSequence10_30Reader.class,
				new MariadbSchema10_27Reader(DialectResolver.getInstance().getDialect("MariaDB", 10, 3, 0)).newSequenceReader());
		assertInstanceOf(MariadbSequence11_50Reader.class,
				new MariadbSchema11_50Reader(DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 0)).newSequenceReader());
	}
}
