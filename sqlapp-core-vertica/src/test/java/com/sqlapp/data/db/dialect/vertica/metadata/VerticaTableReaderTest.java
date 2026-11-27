/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.metadata;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaIndexReader;
import com.sqlapp.data.db.dialect.vertica.metadata.VerticaTableReader;

class VerticaTableReaderTest {
	private static class Reader extends VerticaTableReader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(java.sql.Connection c,
				com.sqlapp.jdbc.sql.node.SqlNode n, com.sqlapp.data.parameter.ParametersContext p,
				com.sqlapp.jdbc.sql.ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 4; i++) {
					final int id = i;
					h.handleResultSetNext(new com.sqlapp.jdbc.ExResultSet(null) {
						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "TABLE_NAME" -> "table" + id;
							case "TABLE_SCHEMA" -> "app";
							default -> null;
							};
						}

						@Override
						public boolean getBoolean(String name) {
							return name.equals("IS_TEMP_TABLE") ? id % 2 == 1 : id >= 2;
						}

						@Override
						public java.sql.Timestamp getTimestamp(String name) {
							return null;
						}

						@Override
						public Object getObject(String name) {
							return null;
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
	void bulkReadRetainsIndependentTemporaryAndFlexFlagsThroughXml() throws Exception {
		Reader reader = new Reader();
		var tables = reader.doGetAll(null, new com.sqlapp.data.parameter.ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(4, tables.size());
		for (int i = 0; i < 4; i++) {
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(tables.get(i).asXml()));
			assertEquals(i % 2 == 1, restored.getSpecifics().get("IS_TEMP_TABLE", Boolean.class));
			assertEquals(i >= 2, restored.getSpecifics().get("IS_FLEXTABLE", Boolean.class));
			if (i >= 2)
				assertEquals(com.sqlapp.data.schemas.Table.TableType.Flex, restored.getTableType());
		}
	}

	@Test
	void textIndexReaderIsAvailableAcrossVersionBoundaries() {
		assertIndexReader(7, 1, 0);
		assertIndexReader(7, 2, 0);
		assertIndexReader(8, 0, 0);
		assertIndexReader(9, 0, 0);
		assertIndexReader(11, 1, 0);
		assertIndexReader(11, 1, 1);
		assertIndexReader(25, 1, 0);
	}

	private void assertIndexReader(int major, int minor, int revision) {
		var dialect = DialectResolver.getInstance().getDialect("Vertica", major, minor, revision);
		var tableReader = new VerticaTableReader(dialect);
		assertInstanceOf(VerticaIndexReader.class, tableReader.newIndexReader());
	}
}
