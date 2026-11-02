package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class VirticaProjectionReaderTest {
	private static class Reader extends VirticaProjectionReader {
		int queries;

		Reader() {
			super(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0));
		}

		@Override
		protected JdbcQueryHandler execute(java.sql.Connection c, SqlNode n, ParametersContext p,
				ResultSetNextHandler h) {
			queries++;
			try {
				for (int i = 0; i < 8; i++) {
					final int projection = i / 4;
					final int column = i % 2;
					h.handleResultSetNext(new ExResultSet(null) {
						boolean missing;

						@Override
						public String getString(String name) {
							return switch (name.toUpperCase(java.util.Locale.ROOT)) {
							case "PROJECTION_SCHEMA" -> "physical";
							case "PROJECTION_NAME" -> "p" + projection;
							case "TABLE_SCHEMA_NAME" -> "source";
							case "TABLE_NAME" -> "t" + projection;
							case "PROJECTION_COLUMN_NAME" -> "col" + column;
							default -> null;
							};
						}

						@Override
						public int getInt(String name) {
							missing = projection == 1 && column == 1;
							return missing ? 0 : 2 - column;
						}

						@Override
						public boolean wasNull() {
							return missing;
						}

						@Override
						public Object getObject(String name) {
							return switch (name) {
							case "PROJECTION_BASENAME" -> "base" + projection;
							case "IS_SEGMENTED" -> false;
							case "IS_SUPER_PROJECTION" -> true;
							case "DATA_TYPE" -> "BIGINT";
							case "ENCODING_TYPE" -> "AUTO";
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
	void nodeRowsCollapseIntoNamedObjectsWithOrderedColumnsInOneQuery() throws Exception {
		Reader reader = new Reader();
		var projections = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(2, projections.size());
		assertEquals(2, projections.get(0).getSortColumns().size(), projections.get(0).asXml());
		Schema schema = new Schema("physical");
		projections.forEach(p -> schema.getProjections().add(p));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		Projection p = restored.getProjections().get("p0");
		assertEquals(2, p.getColumns().size());
		assertEquals("col0", p.getColumns().get(0).getName());
		assertEquals("col1", p.getSortColumns().get(0).getName());
		assertEquals("col0", p.getSortColumns().get(1).getName());
		assertEquals(1, restored.getProjections().get("p1").getSortColumns().size());
		assertEquals("source", p.getTableSchemaName());
		assertEquals("t0", p.getTableName());
		assertEquals("BIGINT", p.getColumns().get(0).getSpecifics().get("DATA_TYPE"));
		assertTrue(p.getDefinition() == null || p.getDefinition().isEmpty());
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(p, com.sqlapp.data.db.sql.SqlType.CREATE));
		p.setDefinition(
				"CREATE PROJECTION physical.p0 AS SELECT col0, col1 FROM source.t0 ORDER BY col1, col0 UNSEGMENTED ALL NODES");
		assertEquals(p.getDefinition().get(0),
				registry.createSql(p, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void readerExtensionIsOptInForDialectVersions() {
		assertNull(DialectResolver.getInstance().getDialect("Vertica", 9, 0, 0).getCatalogReader().getSchemaReader()
				.getProjectionReader());
		assertNotNull(DialectResolver.getInstance().getDialect("Vertica", 11, 1, 1).getCatalogReader().getSchemaReader()
				.getProjectionReader());
		assertNotNull(DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).getCatalogReader().getSchemaReader()
				.getProjectionReader());
	}
}
