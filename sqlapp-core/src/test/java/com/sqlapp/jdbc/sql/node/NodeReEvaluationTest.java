package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlComparisonOperator;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

class NodeReEvaluationTest {
	private Node parse(String sql) {
		return SqlParser.getInstance().parse(DialectResolver.getInstance().getDefaultDialect(), sql);
	}

	private List<Object> values(SqlParameterCollection parameters) {
		return parameters.getBindParameters().stream().flatMap(holder -> holder.stream())
				.map(parameter -> parameter.getValue()).toList();
	}

	@Test
	void loopArrayBindingsUseEachIterationsValuesAndSize() {
		Node node = parse("/*for ids:groups*/ id IN /*ids*/(0) /*end*/");
		Map<String, Object> context = new HashMap<>(Map.of("groups", List.of(List.of(1, 2), List.of(3), List.of(4, 5, 6))));
		SqlParameterCollection parameters = node.eval(context);
		assertEquals(List.of(1, 2, 3, 4, 5, 6), values(parameters));
		assertEquals(6, parameters.getParameterSize());
		assertEquals(6, parameters.getSql().chars().filter(c -> c == '?').count());
		assertNotSame(parameters.getBindParameters().get(0).getBindParameter(),
				parameters.getBindParameters().get(2).getBindParameter());
		assertFalse(node.reEval(context, parameters));
	}

	@Test
	void loopReplacementUsesEachIterationsText() {
		Node node = parse("/*for name:names*/ /*$name*/sample /*end*/");
		SqlParameterCollection parameters = node.eval(new HashMap<>(Map.of("names", List.of("one", "two", "three"))));
		assertEquals("one two three", parameters.getSql().trim().replaceAll("\\s+", " "));
	}

	@Test
	void arrayReEvaluationRefreshesIndividualValuesAndPreservesScalarBindings() {
		Node node = parse("SELECT * FROM t WHERE tenant=/*tenant*/0 AND id IN /*ids*/(0)");
		SqlParameterCollection parameters = node.eval(Map.of("tenant", 1, "ids", List.of(1, 2)));
		String sql = parameters.getSql();
		assertTrue(node.reEval(Map.of("tenant", 2, "ids", List.of(4, 3)), parameters));
		assertEquals(sql, parameters.getSql());
		assertEquals(List.of(2, 3, 4), values(parameters));
		assertEquals(List.of(0, 1, 2), parameters.getBindParameters().stream()
				.map(holder -> holder.getBindParameter().getOrdinal()).toList());
	}

	@Test
	void changedArraySizeDoesNotPartiallyUpdateBindings() {
		Node node = parse("SELECT * FROM t WHERE tenant=/*tenant*/0 AND id IN /*ids*/(0)");
		SqlParameterCollection parameters = node.eval(Map.of("tenant", 1, "ids", List.of(1, 2)));
		for (List<Integer> ids : List.of(List.<Integer>of(), List.of(3), List.of(3, 4, 5))) {
			assertFalse(node.reEval(Map.of("tenant", 2, "ids", ids), parameters));
			assertEquals(List.of(1, 1, 2), values(parameters));
		}
	}

	@Test
	void fixedArrayBindingsDoNotEvaluateMissingContext() {
		Node node = parse("id IN /*ids*/(0)");
		SqlParameterCollection parameters = node.eval(Map.of("ids", List.of(1, 2)));
		parameters.fixed();
		assertTrue(node.reEval(Map.of(), parameters));
		assertEquals(List.of(1, 2), values(parameters));
	}

	@Test
	void emptyArrayCannotGrowWithoutRebuildingSql() {
		Node node = parse("id IN /*ids*/(0)");
		SqlParameterCollection parameters = node.eval(Map.of("ids", List.of()));
		assertFalse(node.reEval(Map.of("ids", List.of(1)), parameters));
		assertEquals(List.of(), values(parameters));
	}

	@Test
	void changedArrayOperatorRequiresNewSql() {
		Node node = parse("id IN /*ids*/(0)");
		ParametersContext context = new ParametersContext();
		context.put("ids", List.of(1, 2));
		SqlParameterCollection parameters = node.eval(context);
		context.putOperator("ids", SqlComparisonOperator.NOT_IN);
		assertFalse(node.reEval(context, parameters));
		assertEquals(List.of(1, 2), values(parameters));
	}

	@Test
	void reEvaluationRetainsArrayValueConversion() {
		Node node = parse("name LIKE /*names*/('sample')");
		ParametersContext context = new ParametersContext();
		context.put("names", List.of("a", "b"));
		context.putOperator("names", SqlComparisonOperator.STARTS_WITH);
		SqlParameterCollection parameters = node.eval(context);
		context.put("names", List.of("c", "d"));
		assertTrue(node.reEval(context, parameters));
		assertEquals(List.of("c%", "d%"), values(parameters));
	}

	@Test
	void rowNumbersUseInternalIdsOnEveryReEvaluation() {
		Table table = new Table("t");
		table.getColumns().add(column -> column.setName("name").setDataType(DataType.VARCHAR));
		for (int i = 0; i < 2; i++) {
			var row = table.newRow();
			row.put("name", "__row_no");
			SchemaUtils.setInternalRowId(row, 10 + i);
			table.getRows().add(row);
		}
		Node node = parse("/*VALUES*/VALUES()/*END*/");
		SqlParameterCollection parameters = node.eval(table);
		assertEquals(List.of(10, "__row_no", 11, "__row_no"), values(parameters));
		for (int base : new int[] { 20, 30 }) {
			for (int i = 0; i < 2; i++) {
				SchemaUtils.setInternalRowId(table.getRows().get(i), base + i);
			}
			assertTrue(node.reEval(table, parameters));
			assertEquals(List.of(base, "__row_no", base + 1, "__row_no"), values(parameters));
		}
	}
}
