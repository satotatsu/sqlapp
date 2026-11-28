package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.MapSqlRegistry;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.util.CommonUtils;

/**
 * Regression tests for the second round of node package review fixes.
 */
class NodeReviewFix2Test {
	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	private Node parse(String sql) {
		return SqlParser.getInstance().parse(dialect, sql);
	}

	private Table table(int count) {
		Table table = new Table("t");
		table.getColumns().add(column -> column.setName("colA").setDataType(DataType.INT));
		for (int i = 0; i < count; i++) {
			Row row = table.newRow();
			row.put("colA", i);
			table.getRows().add(row);
		}
		return table;
	}

	private static <T extends Node> T find(Node node, Class<T> type) {
		if (type.isInstance(node)) {
			return type.cast(node);
		}
		for (Node child : node.getChildNodes()) {
			T found = find(child, type);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	// 7: line and offset refer to the whole SQL

	@Test
	void lineAndOffsetReferToTheSourceSql() {
		Node root = parse("SELECT 1\nFROM t\nWHERE /*if a.unknownMethod()*/ x = 1 /*end*/");
		IfNode node = find(root, IfNode.class);
		assertEquals(3, node.getLine());
		assertEquals(6, node.getOffset());
		Node first = root.getChildNodes().get(0);
		assertEquals(1, first.getLine());
		assertEquals(0, first.getOffset());
	}

	@Test
	void expressionErrorReportsSourceLine() {
		Node root = parse("SELECT 1\nFROM t\nWHERE /*if a.unknownMethod()*/ x = 1 /*end*/");
		Map<String, Object> context = CommonUtils.map();
		context.put("a", 1);
		ExpressionExecutionException e = assertThrows(ExpressionExecutionException.class,
				() -> root.eval(context));
		assertTrue(e.getMessage().contains("lineNumber=[3]"), e.getMessage());
	}

	@Test
	void manuallyBuiltNodeHasSafePosition() {
		SqlPartNode node = new SqlPartNode();
		node.setIndex(100);
		assertEquals(1, node.getLine());
		assertEquals(0, node.getOffset());
	}

	// 8: INTERVAL and TIME ZONE literals are consumed whole

	private String matchedValue(String sql) {
		BindVariableNodeFactory factory = new BindVariableNodeFactory();
		List<BindVariableNode> nodes = new ArrayList<>(factory.parseSql(sql).values());
		assertEquals(1, nodes.size(), sql);
		return nodes.get(0).getMatchText();
	}

	@Test
	void intervalSecondIsConsumedWhole() {
		assertEquals("a = /*x*/INTERVAL '1' SECOND", matchedValue("a = /*x*/INTERVAL '1' SECOND"));
		assertEquals("SELECT ?", parse("SELECT /*x*/INTERVAL '1' SECOND").eval(Map.of("x", 1)).getSql().trim());
	}

	@Test
	void intervalPrecisionIsConsumedWhole() {
		assertEquals("a = /*x*/INTERVAL '100' DAY(3)", matchedValue("a = /*x*/INTERVAL '100' DAY(3)"));
		assertEquals("a = /*x*/INTERVAL '1 2:3:4.5' DAY TO SECOND(3)",
				matchedValue("a = /*x*/INTERVAL '1 2:3:4.5' DAY TO SECOND(3)"));
		assertEquals("a = /*x*/INTERVAL '1-2' YEAR(2) TO MONTH",
				matchedValue("a = /*x*/INTERVAL '1-2' YEAR(2) TO MONTH"));
	}

	@Test
	void timestampWithTimeZoneIsConsumedWhole() {
		assertEquals("a = /*x*/TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+09:00'",
				matchedValue("a = /*x*/TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+09:00'"));
	}

	// 9: ROW without a TableRelation

	@Test
	void rowComparisonWithoutRelationRejectsParentTarget() {
		Node root = parse("/*ROW>(target=PARENT)*/");
		Table table = table(1);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> root.eval((Object) null, p -> p.setRow(table.getRows().get(0))));
		assertTrue(e.getMessage().contains("TableRelation"), e.getMessage());
	}

	@Test
	void rowComparisonWithoutRelationOrTableIsRejected() {
		Node root = parse("/*ROW>(columns=(colA))*/");
		Table table = table(1);
		assertThrows(IllegalArgumentException.class, () -> root.eval((Object) null, p -> p.setRow(table.getRows().get(0))));
	}

	@Test
	void rowComparisonWithoutRelationUsesTable() {
		Node root = parse("/*ROW>(columns=(colA))*/");
		Table table = table(2);
		Row row = table.getRows().get(1);
		SqlParameterCollection parameters = root.eval(table, p -> p.setRow(row));
		assertTrue(parameters.getSql().contains("colA"), parameters.getSql());
		assertEquals(List.of(1), parameters.getBindParameters().stream().flatMap(h -> h.stream())
				.map(BindParameter::getValue).toList());
	}

	// 11: include cycles

	@Test
	void circularIncludeIsRejected() {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("a", "SELECT 1 /*include 'b'*/");
		registry.put("b", "/*include 'a'*/");
		Node root = registry.get("a");
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> root.eval(Map.of()));
		assertTrue(e.getMessage().contains("Circular"), e.getMessage());
	}

	@Test
	void sameIncludeTwiceIsNotCircular() {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("part", "1");
		registry.put("query", "SELECT /*include 'part'*/, /*include 'part'*/");
		assertEquals("SELECT 1, 1", registry.get("query").eval(Map.of()).getSql().trim());
	}

	@Test
	void includeToStringIsNullSafeAndStopsAtCycles() {
		IncludeNode node = new IncludeNode();
		node.setMatchText("/*include 'x'*/");
		node.setExpression("'x'");
		assertEquals("/*include 'x'*/", node.toString());
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("a", "A /*include 'a'*/");
		registry.get("a").toString();
	}

	// 14: runtime exceptions from loops are not wrapped

	@Test
	void forLoopDoesNotWrapRuntimeExceptions() {
		Node root = parse("/*for item : items*/ /*if item.unknownMethod()*/1/*end*/ /*end*/");
		Map<String, Object> context = CommonUtils.map();
		context.put("items", List.of(1));
		assertThrows(ExpressionExecutionException.class, () -> root.eval(context));
	}

	// mixed value types in an array

	@Test
	void arrayOfIncomparableValuesKeepsOrder() {
		Node root = parse("SELECT * FROM t WHERE colA IN /*values*/(1)");
		Map<String, Object> context = CommonUtils.map();
		List<Object> values = List.of(2L, 1, "x");
		context.put("values", values);
		SqlParameterCollection parameters = root.eval(context);
		List<Object> bound = parameters.getBindParameters().stream().flatMap(h -> h.stream())
				.map(BindParameter::getValue).toList();
		assertEquals(values, bound);
		assertSame(values.get(0), bound.get(0));
	}
}
