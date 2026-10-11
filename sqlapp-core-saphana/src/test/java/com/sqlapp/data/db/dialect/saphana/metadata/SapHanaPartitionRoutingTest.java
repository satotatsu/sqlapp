/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SapHanaPartitionRoutingTest {
	@Test
	void skipsEmptySelections() {
		var count = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).isEmpty());
		assertEquals(1, count.get());
	}

	@Test
	void skipsSchemaTableCrossProductRows() {
		var count = new AtomicInteger();
		var reader = new SapHanaTableReader(DialectHolder.defaultDialect) {
			@Override
			protected Table createTable(ExResultSet rs) throws SQLException {
				return new Table(rs.getString("table_name")).setSchemaName(rs.getString("schema_name"));
			}

			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				int query = count.incrementAndGet();
				try {
					for (String schema : query == 1 ? java.util.List.of("A", "B") : java.util.List.of("A")) {
						handler.handleResultSetNext(new ExResultSet(null) {
							@Override
							public String getString(String name) {
								return name.equals("schema_name") ? schema
										: query == 1 && schema.equals("A") ? "T1" : "T2";
							}
						});
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		assertEquals(2, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(2, count.get());
	}
}
