/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SybaseColumnSourceTest {
	private static class Reader extends SybaseColumnReader {
		final List<Map<String, Object>> headers = new ArrayList<>(), sources = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
			queries++;
			if (queries == 2)
				assertEquals(Set.of(100, 200), p.get("definitionIds"));
			SybaseReaderTestSupport.feed(h, queries == 1 ? headers : sources);
			return null;
		}
	}

	private Map<String, Object> column(String table, String name) {
		final Map<String, Object> row = new HashMap<>();
		row.put("catalog_name", "db");
		row.put("schema_name", "dbo");
		row.put("table_name", table);
		row.put("column_name", name);
		row.put("type_name", "varchar");
		row.put("max_length", 20L);
		row.put("precision", 0L);
		row.put("scale", 0);
		row.put("is_nullable", true);
		return row;
	}

	@Test
	void readsManyDefaultsAndComputedChunksInTwoQueriesAndRetainsThemThroughXml() throws Exception {
		final var reader = new Reader();
		for (int i = 0; i < 40; i++) {
			var row = column("items" + i, "value");
			row.put("default_id", 100);
			reader.headers.add(row);
		}
		final var computed = column("items0", "calc");
		computed.put("is_computed", true);
		computed.put("is_computed_persisted", true);
		computed.put("computed_id", 200);
		computed.put("type_name", "int");
		computed.put("max_length", 4L);
		reader.headers.add(computed);
		reader.sources.add(Map.of("definition_id", 100, "definition", "CREATE DEFAULT dbo.d AS 'a "));
		reader.sources.add(Map.of("definition_id", 100, "definition", "b'"));
		reader.sources.add(Map.of("definition_id", 200, "definition", "char_length(value) + "));
		reader.sources.add(Map.of("definition_id", 200, "definition", "1"));
		var columns = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo().setMajorVersion(16));
		assertEquals(2, reader.queries);
		assertEquals(41, columns.size());
		assertEquals("'a b'", columns.get(0).getDefaultValue());
		final Column xml = SchemaUtils.readXml(new java.io.StringReader(columns.get(40).asXml()));
		assertEquals("char_length(value) + 1", xml.getFormula());
		assertTrue(xml.isFormulaPersisted());
	}

	@Test
	void doesNotTurnMissingIdentityValuesIntoZeroOrFetchUnneededText() {
		final var reader = new Reader();
		final var identity = column("items", "id");
		identity.put("type_name", "numeric");
		identity.put("precision", 18L);
		identity.put("max_length", 17L);
		identity.put("is_identity", true);
		reader.headers.add(identity);
		final var column = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals(1, reader.queries);
		assertTrue(column.isIdentity());
		assertEquals(18L, column.getLength());
		assertNull(column.getIdentityStartValue());
		assertNull(column.getIdentityStep());
		assertNull(column.getIdentityLastValue());
		assertEquals("columns.sql", SybaseColumnReader.getColumnSqlResource(null));
	}

	@Test
	void rejectsComputedColumnsWithMissingSourceIdentity() {
		final var reader = new Reader();
		var row = column("items", "calc");
		row.put("is_computed", true);
		reader.headers.add(row);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void rejectsUnavailableAndHiddenColumnSource() {
		final var reader = new Reader() {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries++;
				SybaseReaderTestSupport.feed(h, queries % 2 == 1 ? headers : sources);
				return null;
			}
		};
		var row = column("items", "value");
		row.put("default_id", 100);
		reader.headers.add(row);
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		reader.sources.add(Map.of("definition_id", 100, "definition", "ciphertext", "source_hidden", 1));
		assertTrue(assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null))
				.getMessage().contains("hidden"));
	}
}
