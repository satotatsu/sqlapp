/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class FirebirdTemporaryTableMetadataTest {
	@Test void readsBothTemporaryTableLifetimesInOneBulkQuery() {
		int[] queries = { 0 };
		var reader = new Firebird20TableReader(DialectHolder.defaultDialect25) {
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries[0]++;
				for (int i = 0; i < 50; i++) {
					int type = i % 2 == 0 ? 4 : 5;
					String name = "temporary_" + i;
					var row = new ExResultSet(null) {
						@Override public String getString(String key) { return key.equals("RDB$RELATION_NAME") ? name : null; }
						@Override public Object getObject(String key) { return key.equals("RELATION_TYPE") ? type : null; }
					};
					try { handler.handleResultSetNext(row); } catch (SQLException e) { throw new AssertionError(e); }
				}
				return null;
			}
		};
		var tables = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(50, tables.size());
		assertEquals(1, queries[0]);
		assertTrue(tables.stream().allMatch(table -> table.getTableType() == Table.TableType.Temporary));
		assertEquals("PRESERVE ROWS", tables.get(0).getSpecifics().get("firebird.onCommit"));
		assertEquals("DELETE ROWS", tables.get(1).getSpecifics().get("firebird.onCommit"));
	}

	@Test void keeps21CatalogColumnsOutOf20Queries() {
		var reader = new Firebird20TableReader(DialectHolder.defaultDialect20);
		for (int minor : new int[] { 0, 1, 5 }) {
			var product = new ProductVersionInfo().setMajorVersion(2).setMinorVersion(minor);
			var context = new ParametersContext();
			context.put("readerOptions", new com.sqlapp.data.db.metadata.ReaderOptions());
			var sql = new com.sqlapp.jdbc.sql.SqlParameterCollection();
			reader.getSqlSqlNode(product).eval(context, sql);
			assertEquals(minor >= 1, sql.getSql().contains("R.RDB$RELATION_TYPE"), sql.getSql());
		}
		assertEquals(Firebird20TableReader.class, DialectHolder.defaultDialect25.getCatalogReader().getSchemaReader().getTableReader().getClass());
	}
}
