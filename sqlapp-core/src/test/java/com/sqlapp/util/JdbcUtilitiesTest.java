/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.Node;

class JdbcUtilitiesTest {
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void scalarClosesResourcesAfterSuccessEmptyResultAndFailures(boolean byName) {
		for (String outcome : List.of("success", "empty", "sqlFailure", "conversionFailure")) {
			JdbcStub stub = new JdbcStub();
			stub.empty = outcome.equals("empty");
			stub.readFailure = outcome.equals("sqlFailure") ? new SQLException("read failed") : null;
			stub.value = outcome.equals("conversionFailure") ? "invalid integer" : "42";
			if (outcome.endsWith("Failure")) {
				assertThrows(RuntimeException.class, () -> scalar(stub, byName));
			} else {
				assertEquals(stub.empty ? null : Integer.valueOf(42), scalar(stub, byName));
			}
			assertEquals(List.of("result.close", "statement.close"), stub.closed);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void scalarClosesStatementWhenQueryExecutionFails(boolean byName) {
		JdbcStub stub = new JdbcStub();
		stub.queryFailure = new SQLException("query failed");
		assertSame(stub.queryFailure, assertThrows(RuntimeException.class, () -> scalar(stub, byName)).getCause());
		assertEquals(List.of("statement.close"), stub.closed);
	}

	@Test
	void addAndRetainsTheConditionAndSpacing() {
		assertEquals("select 1 where x = ? AND y = ?",
				new SqlExecuter("select 1 where x = ?").addAnd("y = ?").toString());
	}

	@ParameterizedTest
	@EnumSource(Factory.class)
	void statementFactoriesBindEvaluatedParametersAndReturnOpenStatements(Factory factory) throws Exception {
		JdbcStub stub = new JdbcStub();
		PreparedStatement statement = factory.create(stub.connection(), node(), new ParametersContext());
		assertSame(stub.statement, statement);
		assertEquals(Map.of(1, "bound value"), stub.bindings);
		assertTrue(stub.closed.isEmpty());
		assertEquals("select ?", stub.preparedArguments[0]);
		if (factory.name().endsWith("READONLY")) {
			assertArrayEquals(new Object[] { "select ?", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY },
					stub.preparedArguments);
		}
		statement.close();
		assertEquals(List.of("statement.close"), stub.closed);
	}

	@ParameterizedTest
	@EnumSource(Factory.class)
	void bindingFailureClosesStatementAndPreservesTheCause(Factory factory) {
		JdbcStub stub = new JdbcStub();
		stub.bindFailure = new SQLException("bind failed");
		stub.closeFailure = new SQLException("close failed");
		Exception failure = assertThrows(Exception.class,
				() -> factory.create(stub.connection(), node(), new ParametersContext()));
		Throwable cause = failure instanceof SQLException ? failure : failure.getCause();
		assertSame(stub.bindFailure, cause);
		assertArrayEquals(new Throwable[] { stub.closeFailure }, cause.getSuppressed());
		assertEquals(List.of("statement.close"), stub.closed);
	}

	private Integer scalar(JdbcStub stub, boolean byName) {
		SqlExecuter executer = new SqlExecuter("select value");
		return byName ? executer.executeScalar(stub.connection(), Integer.class, "value")
				: executer.executeScalar(stub.connection(), Integer.class, 1);
	}

	private Node node() {
		return new Node() {
			@Override public boolean eval(Object context, SqlParameterCollection parameters) {
				parameters.addSql("select");
				BindParameter parameter = new BindParameter();
				parameter.setDataType(DataType.VARCHAR);
				parameter.setValue("bound value");
				parameters.add(parameter);
				return true;
			}
		};
	}

	private enum Factory {
		PREPARED, PREPARED_READONLY, CALLABLE, CALLABLE_READONLY;

		PreparedStatement create(Connection connection, Node node, ParametersContext context) throws SQLException {
			return switch (this) {
			case PREPARED -> StatementUtils.createPreparedStatement(connection, node, context);
			case PREPARED_READONLY -> StatementUtils.createPreparedStatementReadonly(connection, node, context);
			case CALLABLE -> StatementUtils.createCallableStatement(connection, node, context);
			case CALLABLE_READONLY -> StatementUtils.createCallableStatementReadonly(connection, node, context);
			};
		}
	}

	private static class JdbcStub {
		final List<String> closed = new ArrayList<>();
		final Map<Integer, Object> bindings = new HashMap<>();
		Object[] preparedArguments;
		Object value;
		boolean empty;
		SQLException readFailure;
		SQLException queryFailure;
		SQLException bindFailure;
		SQLException closeFailure;
		final ResultSet result = proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
		case "next" -> !empty;
		case "getObject" -> {
			if (readFailure != null) { throw readFailure; }
			yield value;
		}
		case "close" -> { closed.add("result.close"); yield null; }
		default -> throw new UnsupportedOperationException(method.getName());
		});
		final CallableStatement statement = proxy(CallableStatement.class, (object, method, args) -> {
			if (method.getName().startsWith("set")) {
				if (bindFailure != null) { throw bindFailure; }
				bindings.put((Integer) args[0], args[1]);
				return null;
			}
			return switch (method.getName()) {
			case "executeQuery" -> {
				if (queryFailure != null) { throw queryFailure; }
				yield result;
			}
			case "close" -> {
				closed.add("statement.close");
				if (closeFailure != null) { throw closeFailure; }
				yield null;
			}
			default -> throw new UnsupportedOperationException(method.getName());
			};
		});

		Connection connection() {
			return proxy(Connection.class, (object, method, args) -> {
				if (method.getName().equals("prepareStatement") || method.getName().equals("prepareCall")) {
					preparedArguments = args;
					return statement;
				}
				throw new UnsupportedOperationException(method.getName());
			});
		}
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
	}
}
