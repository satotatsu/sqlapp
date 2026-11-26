package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

class JdbcExecutionRegressionTest {

	@Test
	void consumesUpdateCountsAndResultSetsUntilBothAreAbsent() throws Exception {
		List<Object> results = List.of(4L, "rows", 3_000_000_000L, "rows", 2L);
		AtomicInteger position = new AtomicInteger(-1);
		AtomicInteger closed = new AtomicInteger();
		ResultSet rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
				(proxy, method, args) -> {
					if (method.getName().equals("close")) {
						closed.incrementAndGet();
						return null;
					}
					throw new AssertionError(method.getName());
				});
		PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
					if (method.getName().equals("getMoreResults")) {
						int index = position.incrementAndGet();
						return index < results.size() && results.get(index) instanceof String;
					}
					if (method.getName().equals("getLargeUpdateCount")) {
						int index = position.get();
						return index < results.size() && results.get(index) instanceof Long ? results.get(index) : -1L;
					}
					if (method.getName().equals("getResultSet")) {
						return rows;
					}
					throw new AssertionError(method.getName());
				});
		AtomicInteger handled = new AtomicInteger();
		JdbcHandler handler = new JdbcHandler(new SqlNode("unused")) {
			@Override
			protected void handleResultSet(ExResultSet resultSet) {
				handled.incrementAndGet();
			}
			@Override
			protected void handleGeneratedKeys(PreparedStatement ignored) {
			}
		};
		handler.handleMoreResults(statement);
		assertEquals(3_000_000_006L, handler.getUpdateCount());
		assertEquals(2, handled.get());
		assertEquals(2, closed.get());
		assertEquals(results.size(), position.get());
	}

	@Test
	void registersInoutAndUsesFlattenedParameterPositionsInBothBindingPaths() throws Exception {
		List<Integer> inputs = new ArrayList<>();
		List<Integer> outputs = new ArrayList<>();
		CallableStatement statement = (CallableStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { CallableStatement.class }, (proxy, method, args) -> {
					if (method.getName().equals("registerOutParameter")) {
						outputs.add((Integer) args[0]);
					} else if (method.getName().startsWith("set")) {
						inputs.add((Integer) args[0]);
					} else {
						throw new AssertionError(method.getName());
					}
					return null;
				});
		SqlParameterCollection parameters = new SqlParameterCollection(null);
		BindParameterHolder group = new BindParameterHolder();
		group.getBindParameters().add(parameter(ParameterDirection.Input));
		group.getBindParameters().add(parameter(ParameterDirection.Inout));
		parameters.add(group);
		parameters.add(parameter(ParameterDirection.Output));
		CallableJdbcHandler handler = new CallableJdbcHandler(new SqlNode("unused"));
		handler.setBind(statement, parameters);
		assertEquals(List.of(1, 2), inputs);
		assertEquals(List.of(2, 3), outputs);
		inputs.clear();
		outputs.clear();
		handler.setBind((PreparedStatement) statement, parameters);
		assertEquals(List.of(1, 2), inputs);
		assertEquals(List.of(2, 3), outputs);
	}

	@Test
	void outputWithoutTypeFailsWithAnActionableError() {
		CallableJdbcHandler handler = new CallableJdbcHandler(new SqlNode("unused"));
		BindParameter parameter = parameter(ParameterDirection.Inout);
		parameter.setDataType(null);
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> handler.registerOutParameter(null, null, parameter, 1));
		assertTrue(error.getMessage().contains("requires a data type"));
	}

	private BindParameter parameter(ParameterDirection direction) {
		BindParameter parameter = new BindParameter();
		parameter.setName("value");
		parameter.setDataType(DataType.INT);
		parameter.setDirection(direction);
		parameter.setValue(7);
		return parameter;
	}
}
