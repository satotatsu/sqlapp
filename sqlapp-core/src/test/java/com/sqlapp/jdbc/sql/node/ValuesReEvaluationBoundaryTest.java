package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

class ValuesReEvaluationBoundaryTest {
	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	private Table table(int count, int first) {
		Table table = new Table("t");
		table.getColumns().add(column -> column.setName("id").setDataType(DataType.INT));
		for (int i = 0; i < count; i++) {
			var row = table.newRow();
			row.put("id", first + i);
			table.getRows().add(row);
		}
		return table;
	}

	private Node parse(String sql) {
		return SqlParser.getInstance().parse(dialect, sql);
	}

	private List<Object> values(SqlParameterCollection parameters) {
		return parameters.getBindParameters().stream().flatMap(holder -> holder.stream())
				.map(BindParameter::getValue).toList();
	}

	@Test
	void changedRowCountRejectsBeforeUpdatingScalarOrGroupedValues() {
		Node node = parse("SELECT /*version*/0; /*VALUES*/VALUES()/*END*/");
		SqlParameterCollection parameters = node.eval(Map.of("version", 1, "rows", table(2, 10).getRows()));
		for (int size : new int[] { 0, 1, 3, 4 }) {
			assertFalse(node.reEval(Map.of("version", 2, "rows", table(size, 20).getRows()), parameters));
			assertEquals(List.of(1, 10, 11), values(parameters));
		}
	}

	@Test
	void originalTableMutationDoesNotChangeRecordedRowCount() {
		Node node = parse("/*VALUES*/VALUES()/*END*/");
		Table table = table(2, 10);
		SqlParameterCollection parameters = node.eval(table);
		var row = table.newRow();
		row.put("id", 12);
		table.getRows().add(row);
		assertFalse(node.reEval(table, parameters));
		assertEquals(List.of(10, 11), values(parameters));
	}

	@Test
	void sameRowCountUpdatesEachRow() {
		Node node = parse("/*VALUES*/VALUES(/*id*/0)/*END*/");
		SqlParameterCollection parameters = node.eval(table(2, 10));
		assertTrue(node.reEval(table(2, 20), parameters));
		assertEquals(List.of(20, 21), values(parameters));
	}

	@Test
	void allFixedGroupedValuesDoNotRequireRowContext() {
		Node node = parse("/*VALUES*/VALUES()/*END*/");
		SqlParameterCollection parameters = node.eval(table(2, 10));
		parameters.fixed();
		assertTrue(node.reEval(Map.of(), parameters));
		assertEquals(List.of(10, 11), values(parameters));
	}

	@Test
	void partiallyFixedGroupUpdatesOnlyUnfixedValues() {
		Node node = parse("/*VALUES*/VALUES()/*END*/");
		SqlParameterCollection parameters = node.eval(table(2, 10));
		parameters.getBindParameters().get(0).getBindParameters().get(0).fixed();
		assertTrue(node.reEval(table(2, 20), parameters));
		assertEquals(List.of(10, 21), values(parameters));
	}

	@Test
	void cloneRetainsRowCountAndIndependentValues() {
		Node node = parse("/*VALUES*/VALUES()/*END*/");
		SqlParameterCollection original = node.eval(table(2, 10));
		SqlParameterCollection copy = original.clone();
		assertEquals(2, copy.getBindParameters().get(0).getRowCount());
		assertFalse(node.reEval(table(1, 20), copy));
		assertTrue(node.reEval(table(2, 20), copy));
		assertEquals(List.of(20, 21), values(copy));
		assertEquals(List.of(10, 11), values(original));
	}

	@Test
	void sampleLiteralsContinueToExpandRowsForBothSqlStrategies() {
		for (boolean supportsValues : new boolean[] { false, true }) {
			Dialect dialect = new Dialect(() -> null) {
				@Override
				public boolean supportsValues() {
					return supportsValues;
				}
			};
			Node node = SqlParser.getInstance().parse(dialect, "/*VALUES*/VALUES(42)/*END*/");
			SqlParameterCollection parameters = node.eval(table(2, 10));
			assertEquals(List.of(10, 11), values(parameters));
			assertEquals(2, parameters.getSql().chars().filter(c -> c == '?').count());
			assertFalse(parameters.getSql().contains("42"));
			assertTrue(node.reEval(table(2, 20), parameters));
			assertEquals(List.of(20, 21), values(parameters));
		}
	}

	@Test
	void emptyReplacementProducesEmptySqlFragment() {
		Node node = parse("SELECT /*$fragment*/sample FROM t");
		assertEquals("SELECT  FROM t", node.eval(Map.of("fragment", "")).getSql());
	}

	@Test
	void unknownGroupShapeRequiresReEvaluation() {
		SqlParameterCollection parameters = new SqlParameterCollection(dialect);
		BindParameterHolder group = new BindParameterHolder();
		BindParameter value = new BindParameter();
		value.setColumn(table(1, 10).getColumns().get("id"));
		value.setValue(10);
		group.getBindParameters().add(value);
		parameters.add(group);
		assertFalse(parse("SELECT 1").reEval(table(1, 20), parameters));
		assertEquals(List.of(10), values(parameters));
	}

	@Test
	void initialEmptyValuesFailsWithActionableError() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> parse("/*VALUES*/VALUES()/*END*/").eval(table(0, 10)));
		assertTrue(error.getMessage().contains("at least one source row"));
	}
}
