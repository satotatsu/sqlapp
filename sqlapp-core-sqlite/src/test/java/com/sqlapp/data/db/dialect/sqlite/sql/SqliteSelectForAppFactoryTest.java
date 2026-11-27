/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class SqliteSelectForAppFactoryTest {
	private Table setup(Connection connection) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY,label TEXT)");
			statement.execute("INSERT INTO t VALUES(1,'odd'),(2,'even'),(3,'odd'),(4,'even'),(5,'odd')");
		}
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("main");
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private SqlNode node(Table table) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT_FOR_APP)
				.get(0);
	}

	private ParametersContext context(Table table, Integer rows, Integer offset) {
		var context = new ParametersContext(table);
		context.put("_orderBy", "id");
		if (rows != null)
			context.put("_row", rows);
		if (offset != null)
			context.put("_offset", offset);
		return context;
	}

	private List<Integer> values(Connection connection, SqlNode node, ParametersContext context) throws Exception {
		try (var parameters = node.eval(context); var query = parameters.createStatement(connection)) {
			parameters.setBind(query);
			try (var result = query.executeQuery()) {
				var ids = new ArrayList<Integer>();
				while (result.next())
					ids.add(result.getInt(1));
				return ids;
			}
		}
	}

	@Test
	void limitOffsetZeroAndUnpagedQueriesUseNativeSql() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var table = setup(connection);
			var node = node(table);
			assertEquals(List.of(2, 3), values(connection, node, context(table, 2, 1)));
			assertEquals(List.of(1, 2), values(connection, node, context(table, 2, null)));
			assertEquals(List.of(3, 4, 5), values(connection, node, context(table, null, 2)));
			assertEquals(List.of(1, 2, 3, 4, 5), values(connection, node, context(table, null, null)));
			assertEquals(List.of(), values(connection, node, context(table, 0, 0)));
			assertEquals(List.of(), values(connection, node, context(table, 2, 20)));
			assertNotNull(node.getRowParameter());
			assertNotNull(node.getOffsetParameter());
		}
	}

	@Test
	void countModeRetainsFiltersAndOmitsOrderingAndPaging() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var table = setup(connection);
			var node = node(table);
			var context = context(table, 1, 1);
			context.put("label", "odd");
			assertEquals(List.of(3), values(connection, node, context));
			context.setCountSqlMode();
			try (var parameters = node.eval(context)) {
				assertFalse(parameters.getSql().contains("LIMIT"));
				assertFalse(parameters.getSql().contains("OFFSET"));
				assertFalse(parameters.getSql().contains("ORDER BY"));
			}
			assertEquals(List.of(3), values(connection, node, context));
		}
	}

	@Test
	void queryHandlerArgumentsApplyOnceWithoutScrollableResults() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var table = setup(connection);
			var ids = new ArrayList<Integer>();
			var handler = new JdbcQueryHandler(node(table), new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet result) throws java.sql.SQLException {
					ids.add(result.getInt(1));
				}
			});
			handler.execute(connection, context(table, null, null), 2, 1);
			assertEquals(List.of(2, 3), ids);
			ids.clear();
			try (var parameters = node(table).eval(context(table, 2, 1))) {
				handler.execute(connection, parameters, 2, 1);
				assertEquals(List.of(2, 3), ids);
			}
		}
	}

	@Test
	void invalidNativeLimitIsNotSilentlyOmitted() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var table = setup(connection);
			var context = context(table, null, null);
			context.put("_row", "invalid-limit");
			assertThrows(java.sql.SQLException.class, () -> values(connection, node(table), context));
		}
	}

	@Test
	void legacySelectionAndDescendingOrderKeepPagingSemantics() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			var table = setup(connection);
			var dialect = DialectResolver.getInstance().getDialect("SQLite", 3, 7, 0);
			var node = dialect.createSqlFactoryRegistry().createSqlNodes(table, SqlType.SELECT_FOR_APP).get(0);
			var context = context(table, 2, 1);
			context.put("_orderBy", "id DESC");
			assertEquals(List.of(4, 3), values(connection, node, context));
		}
	}
}
