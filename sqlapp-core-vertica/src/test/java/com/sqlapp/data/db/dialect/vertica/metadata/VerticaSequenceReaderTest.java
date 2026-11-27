/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaSequenceReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class VerticaSequenceReaderTest {
	private static class Reader extends VerticaSequenceReader {
		long cache = 20;
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 3; i++) {
					final int id = i;
					h.handleResultSetNext(new ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "SEQUENCE_NAME" -> "seq" + id;
							case "SEQUENCE_SCHEMA" -> "app";
							case "SEQUENCE_ID" -> Integer.toString(id);
							case "COMMENT" -> "sequence comment";
							default -> null;
							};
						}

						@Override
						public long getLong(String name) {
							return switch (name) {
							case "SESSION_CACHE_COUNT" -> cache;
							case "CURRENT_VALUE" -> 4000;
							case "MAXIMUM" -> Long.MAX_VALUE;
							default -> 1;
							};
						}

						@Override
						public boolean getBoolean(String name) {
							return false;
						}

						@Override
						public Object getObject(String name) {
							return "owner";
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
	void readsAllSequencesOnceAndPreservesAllocationSeparatelyThroughXml() throws Exception {
		var reader = new Reader();
		var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(3, sequences.size());
		Sequence restored = SchemaUtils.readXml(new java.io.StringReader(sequences.get(0).asXml()));
		assertEquals(DataType.BIGINT, restored.getDataType());
		assertNull(restored.getLastValue());
		assertNull(restored.getStartValue());
		assertEquals("4000", restored.getSpecifics().get("CURRENT_VALUE"));
		assertEquals(20, restored.getCacheSize());
		String ddl = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry()
				.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(ddl.contains("CACHE 20"), ddl);
		assertFalse(ddl.contains("START WITH 4000"), ddl);
	}

	@Test
	void rejectsCacheOverflowAndNegativeValuesWithoutTruncation() {
		var reader = new Reader();
		reader.cache = (long) Integer.MAX_VALUE + 1;
		assertThrows(ArithmeticException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		reader.cache = -1;
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
