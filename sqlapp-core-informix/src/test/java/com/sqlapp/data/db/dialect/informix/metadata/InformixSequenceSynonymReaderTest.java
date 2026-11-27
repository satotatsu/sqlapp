/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixSequenceSynonymReaderTest {
	private static class Rows {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		JdbcQueryHandler feed(ResultSetNextHandler handler) {
			queries++;
			try {
				for (var row : rows)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}
	}

	@Test
	void readsSequenceOrderCacheCycleAnd64BitBoundsInOneQuery() {
		final var data = new Rows();
		for (int i = 0; i < 20; i++) {
			final Map<String, Object> row = new HashMap<>();
			row.put("sequence_name", "seq" + i);
			row.put("catalog_name", "db");
			row.put("schema_name", "owner");
			row.put("start_value", 3000000000L);
			row.put("minimum_value", -9000000000L);
			row.put("maximum_value", 9000000000L);
			row.put("increment_value", -2L);
			row.put("cycle_option", i % 2 == 0 ? "0" : "1");
			row.put("order_option", i % 2 == 0 ? "0" : "1");
			row.put("cache_size", i % 2 == 0 ? 0L : 20L);
			data.rows.add(row);
		}
		final var reader = new InformixSequenceReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(h);
			}
		};
		final var sequences = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(20, sequences.size());
		for (int i = 0; i < sequences.size(); i++) {
			final var sequence = sequences.get(i);
			assertEquals(java.math.BigInteger.valueOf(3000000000L), sequence.getStartValue());
			assertEquals(java.math.BigInteger.valueOf(-9000000000L), sequence.getMinValue());
			assertEquals(i % 2 == 1, sequence.isCycle());
			assertEquals(i % 2 == 1, sequence.isOrder());
			assertEquals(i % 2 == 1, sequence.isCache());
		}
		data.rows.get(0).put("order_option", "?");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		data.rows.get(0).put("order_option", "0");
		data.rows.get(0).put("cycle_option", "?");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void readsSynonymIdentityVisibilityAndAnsiModeInOneQuery() {
		final var data = new Rows();
		for (int i = 0; i < 20; i++) {
			final Map<String, Object> row = new HashMap<>();
			row.put("catalog_name", "db");
			row.put("schema_name", "owner" + i);
			row.put("object_name", "alias");
			row.put("base_schema", "target_owner");
			row.put("base_object", "items");
			row.put("synonym_type", "P");
			row.put("ansi_database", i % 2 == 0 ? "0" : "1");
			data.rows.add(row);
		}
		final var reader = new InformixSynonymReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(h);
			}
		};
		final var synonyms = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(20, synonyms.size());
		for (int i = 0; i < synonyms.size(); i++) {
			assertEquals("db", synonyms.get(i).getCatalogName());
			assertEquals("owner" + i, synonyms.get(i).getSchemaName());
			assertEquals(Boolean.toString(i % 2 == 1),
					synonyms.get(i).getSpecifics().get(InformixSynonymReader.INFORMIX_ANSI_DATABASE));
			assertFalse(synonyms.get(i).getSpecifics().containsKey(InformixSynonymReader.INFORMIX_DATABASE_NAME));
		}
		data.rows.get(0).put("ansi_database", null);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		data.rows.get(0).put("ansi_database", "0");
		data.rows.get(0).put("base_object", null);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}
}
