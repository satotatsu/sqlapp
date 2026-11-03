/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaIndexReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class VerticaIndexReaderTest {
	private static class Reader extends VerticaIndexReader {
		int queries;

		Reader(int major) {
			super(DialectResolver.getInstance().getDialect("Vertica", major, 0, 0));
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
							case "INDEX_NAME" -> "text index " + id;
							case "INDEX_ID" -> Integer.toString(id);
							case "SOURCE_TABLE_SCHEMA_NAME" -> "source schema";
							case "SOURCE_TABLE_NAME" -> "source table";
							case "TEXT_COL" -> "body";
							default -> null;
							};
						}

						@Override
						public Object getObject(String name) {
							return switch (name) {
							case "INDEX_SCHEMA_NAME" -> "index schema";
							case "TOKENIZER_NAME" -> "StringTokenizer";
							case "TOKENIZER_SCHEMA_NAME" -> "public";
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
	void bulkMetadataPreservesCrossSchemaIdentityAndDoesNotPretendToRecreate() throws Exception {
		for (int major : new int[] { 7, 8, 9, 11, 12, 25 }) {
			Reader reader = new Reader(major);
			var indexes = reader.doGetAll(null, new ParametersContext(), null);
			assertEquals(1, reader.queries);
			assertEquals(3, indexes.size());
			for (Index index : indexes) {
				Table table = new Table(index.getTableName());
				table.setSchemaName(index.getSchemaName());
				table.getIndexes().add(index);
				Table restoredTable = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				Index restored = restoredTable.getIndexes().get(0);
				assertEquals("source schema", restored.getSchemaName());
				assertEquals("source table", restored.getTableName());
				assertEquals("index schema", restored.getSpecifics().get("INDEX_SCHEMA_NAME", String.class));
				assertEquals("StringTokenizer", restored.getSpecifics().get("TOKENIZER_NAME", String.class));
				assertEquals(IndexType.FullText, restored.getIndexType());
				assertEquals("body", restored.getColumns().get(0).getName());
				var registry = DialectResolver.getInstance().getDialect("Vertica", major, 0, 0)
						.createSqlFactoryRegistry();
				var error = assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(restored, SqlType.CREATE));
				assertTrue(error.getMessage().contains("ID column first"), error.getMessage());
			}
		}
	}
}
