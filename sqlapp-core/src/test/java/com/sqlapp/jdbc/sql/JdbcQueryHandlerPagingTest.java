/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.node.SqlNode;

class JdbcQueryHandlerPagingTest {
	private static class Capture extends JdbcQueryHandler {
		List<Object> bindings;
		boolean scroll;
		Capture(SqlNode node) { super(node, null); }
		@Override protected StatementSqlParametersHolder createStatement(Connection connection, Object context, Integer limit) {
			var parameters = getNode().eval(context);
			createStatement(connection, parameters, limit);
			return new StatementSqlParametersHolder(null, parameters);
		}
		@Override protected PreparedStatement createStatement(Connection connection, SqlParameterCollection parameters, Integer limit) {
			bindings = parameters.getBindParameters().stream().flatMap(p -> p.stream()).map(p -> p.getValue()).toList();
			return null;
		}
		@Override protected PreparedStatement createStatementScrollInsensitive(Connection connection, SqlParameterCollection parameters) {
			scroll = true;
			return createStatement(connection, parameters, null);
		}
		@Override protected StatementSqlParametersHolder createStatementScrollInsensitive(Connection connection, ParametersContext context) {
			scroll = true;
			return createStatement(connection, context, null);
		}
		@Override protected void executeQueryCallback(Connection connection, PreparedStatement statement, Integer limit, Integer offset) { }
	}

	private Capture handler(String sql) {
		return new Capture(SqlParser.getInstance().parse(DialectResolver.getInstance().getDefaultDialect(), sql));
	}

	@Test
	void explicitLimitAndOffsetReachNativeSqlParameters() throws Exception {
		var handler = handler("SELECT 1 LIMIT /*_row*/1 OFFSET /*_offset*/0");
		var context = new ParametersContext();
		context.put("_row", 99);
		handler.execute(null, context, 2, 1);
		assertEquals(List.of(2, 1), handler.bindings);
		assertFalse(handler.scroll);
	}

	@Test
	void zeroLimitIsNotLostAndNullLimitPreservesContextValue() throws Exception {
		var handler = handler("SELECT 1 LIMIT /*_row*/1");
		var context = new ParametersContext();
		handler.execute(null, context, 0, null);
		assertEquals(List.of(0), handler.bindings);
		context.put("_row", 7);
		handler.execute(null, context, null, null);
		assertEquals(List.of(7), handler.bindings);
	}

	@Test
	void evaluatedNativeOffsetParametersDoNotRequestScrollableResults() throws Exception {
		var handler = handler("SELECT 1 LIMIT /*_row*/1 OFFSET /*_offset*/0");
		var context = new ParametersContext();
		context.put("_row", 2);
		context.put("_offset", 1);
		try (var parameters = handler.getNode().eval(context)) {
			handler.execute(null, parameters, 2, 1);
			assertEquals(List.of(2, 1), handler.bindings);
			assertFalse(handler.scroll);
		}
	}

	@Test
	void evaluatedSqlWithoutOffsetRetainsScrollableFallback() throws Exception {
		var handler = handler("SELECT 1");
		try (var parameters = handler.getNode().eval(new ParametersContext())) {
			handler.execute(null, parameters, 2, 1);
			assertTrue(handler.scroll);
		}
	}

	@Test
	void sqlWithoutPagingParametersRetainsClientSideFallback() throws Exception {
		var handler = handler("SELECT 1");
		var context = new ParametersContext();
		handler.execute(null, context, 2, 1);
		assertFalse(context.containsKeyInternal("_row"));
		assertTrue(handler.scroll);
		assertTrue(handler.bindings.isEmpty());
	}
}
