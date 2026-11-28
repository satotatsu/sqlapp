package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.MapSqlRegistry;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

class NodeRegressionTest {

	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	@Test
	void includePreservesRegisteredSqlAcrossEvaluations() {
		SqlNode included = SqlParser.getInstance().parse(dialect, "id=/*id*/0");
		MapSqlRegistry registry = new MapSqlRegistry() {
			@Override
			public SqlNode get(String sqlId, Dialect requestedDialect) {
				assertEquals("part", sqlId);
				assertSame(dialect, requestedDialect);
				return included;
			}
		};
		SqlNode node = new SqlNode("SELECT * FROM t WHERE ");
		node.setDialect(dialect);
		IncludeNode include = new IncludeNode();
		include.setExpression("'part'");
		node.addChildNode(include);
		node.setSqlRegistry(registry);
		for (int value : new int[] { 1, 2 }) {
			SqlParameterCollection parameters = node.eval(Map.of("id", value));
			assertEquals("SELECT * FROM t WHERE id = ?", parameters.getSql().trim());
			assertEquals(1, parameters.getParameterSize());
			assertEquals(value, parameters.getBindParameters().get(0).getBindParameter().getValue());
			assertFalse(included.getChildNodes().isEmpty());
		}
	}

	@Test
	void clonePreservesOriginalAndCopiesNestedNodes() {
		SqlNode original = SqlParser.getInstance().parse(dialect, "/*for a:b*/ x=/*a*/0 /*end*/");
		SqlNode copy = original.clone();
		assertFalse(original.getChildNodes().isEmpty());
		assertNotSame(original.getChildNodes(), copy.getChildNodes());
		assertCopiedChildren(original, copy);
		Map<String, Object> context = new HashMap<>(Map.of("b", List.of(1)));
		assertEquals(original.eval(context).getSql(), copy.eval(context).getSql());
		copy.getChildNodes().clear();
		assertFalse(original.getChildNodes().isEmpty());
	}

	private void assertCopiedChildren(Node original, Node copy) {
		assertEquals(original.getChildNodes().size(), copy.getChildNodes().size());
		for (int i = 0; i < original.getChildNodes().size(); i++) {
			Node child = original.getChildNodes().get(i);
			Node copiedChild = copy.getChildNodes().get(i);
			assertNotSame(child, copiedChild);
			assertSame(original, child.getParent());
			assertSame(copy, copiedChild.getParent());
			assertCopiedChildren(child, copiedChild);
		}
	}

	@Test
	void loopBindsEveryElement() {
		assertLoopBindings(new HashMap<>(Map.of("b", List.of(1, 2, 3))));
	}

	@Test
	void loopShadowsMapVariableWithoutChangingInput() {
		Map<String, Object> context = new HashMap<>(Map.of("a", 99, "a_index", 42, "b", List.of(1, 2, 3)));
		assertLoopBindings(context);
		assertEquals(99, context.get("a"));
		assertEquals(42, context.get("a_index"));
	}

	@Test
	void loopBindsEveryElementInParametersContext() {
		ParametersContext context = new ParametersContext();
		context.put("a", 99);
		context.put("b", List.of(1, 2, 3));
		assertLoopBindings(context);
		assertEquals(99, context.get("a"));
	}

	private void assertLoopBindings(Object context) {
		Node node = SqlParser.getInstance().parse(dialect, "/*for a:b*/ x=/*a*/0 /*end*/");
		SqlParameterCollection parameters = node.eval(context);
		assertEquals("x = ? x = ? x = ?", parameters.getSql().trim().replaceAll("\\s+", " "));
		assertEquals(3, parameters.getParameterSize());
		assertEquals(List.of(1, 2, 3), parameters.getBindParameters().stream()
				.map(holder -> holder.getBindParameter().getValue()).toList());
		assertNotSame(parameters.getBindParameters().get(0).getBindParameter(),
				parameters.getBindParameters().get(1).getBindParameter());
	}

	@Test
	void inSplitsAtBoundaryWithoutEmptyGroups() {
		assertArrayConditions("IN", " OR ");
	}

	@Test
	void notInSplitsWithAndWithoutEmptyGroups() {
		assertArrayConditions("NOT IN", " AND ");
	}

	private void assertArrayConditions(String operator, String conjunction) {
		for (int size : new int[] { 1, 999, 1000, 1001, 1999, 2000, 2001, 3000 }) {
			Node node = SqlParser.getInstance().parse(dialect, "id IN /*ids*/(0)");
			List<Integer> values = IntStream.rangeClosed(1, size).boxed().toList();
			ParametersContext context = new ParametersContext();
			context.put("ids", values);
			context.putOperator("ids", "NOT IN".equals(operator) ? SqlComparisonOperator.NOT_IN : SqlComparisonOperator.IN);
			SqlParameterCollection parameters = node.eval(context);
			String expected = IntStream.range(0, (size + 999) / 1000).mapToObj(group -> {
				int count = Math.min(1000, size - group * 1000);
				return "id " + operator + " (" + String.join(",", java.util.Collections.nCopies(count, "?")) + ")";
			}).collect(java.util.stream.Collectors.joining(conjunction));
			if (size > 1000) {
				expected = "( " + expected + " )";
			}
			assertEquals(expected, parameters.getSql().trim(), operator + " size=" + size);
			assertEquals(size, parameters.getParameterSize());
			assertEquals(values, parameters.getBindParameters().stream()
					.map(holder -> holder.getBindParameter().getValue()).toList());
		}
	}
}
