package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

class SqlNodeBoundaryTest {
	private SqlNode parse(String sql) {
		return SqlParser.getInstance().parse(null, sql);
	}

	private List<Object> values(SqlParameterCollection parameters) {
		return parameters.getBindParameters().stream().flatMap(BindParameterHolder::stream)
				.map(BindParameter::getValue).toList();
	}

	@Test
	void eachArrayLoopIterationOwnsItsValuesAndSize() {
		SqlNode node = parse("/*for ids:groups*/ id IN /*ids*/(0) /*end*/");
		for (Object context : List.of(
				new LinkedHashMap<>(Map.of("groups", List.of(List.of(1, 2), List.of(3, 4, 5)))),
				new ParametersContext())) {
			if (context instanceof ParametersContext) {
				((ParametersContext) context).put("groups", List.of(List.of(1, 2), List.of(3, 4, 5)));
			}
			SqlParameterCollection result = node.eval(context);
			assertEquals("id IN (?,?) id IN (?,?,?)", result.getSql().trim().replaceAll("\\s+", " "));
			assertEquals(List.of(1, 2, 3, 4, 5), values(result));
			assertEquals(5, result.getParameterSize());
			assertNotSame(result.getBindParameters().get(0).getBindParameter(),
					result.getBindParameters().get(2).getBindParameter());
		}
	}

	@Test
	void immutableAndWrappedMapsSupportLoopVariableShadowing() {
		SqlNode node = parse("/*for x:items*/ /*x*/0 /*end*/");
		Map<String, Object> original = Map.of("x", 99, "x_index", 88, "items", List.of(1, 2));
		for (Map<String, Object> context : List.of(original, Collections.unmodifiableMap(new LinkedHashMap<>(original)))) {
			assertEquals(List.of(1, 2), values(node.eval(context)));
			assertEquals(original, context);
		}
	}

	@Test
	void inlineElseBindsAndNestedDirectivesAreEvaluatedOnce() {
		SqlNode node = parse("SELECT /*if flag*/ /*a*/0\n-- else /*b*/0 + /*c*/0\n/*end*/");
		for (boolean flag : new boolean[] { true, false }) {
			SqlParameterCollection result = node.eval(Map.of("flag", flag, "a", 1, "b", 2, "c", 3));
			assertEquals(flag ? "SELECT ?" : "SELECT ? + ?", result.getSql().trim().replaceAll("\\s+", " "));
			assertEquals(flag ? List.of(1) : List.of(2, 3), values(result));
		}
		SqlNode nested = parse("SELECT /*if flag*/ 1\n-- else /*if nested*/ /*v*/0 /*end*/\n/*end*/");
		assertEquals(List.of(7), values(nested.eval(Map.of("flag", false, "nested", true, "v", 7))));
		assertNotNull(node.getParameterByName("b"));
	}

	@Test
	void directNodesAndNullParameterDefinitionsHaveEmptyMetadata() {
		SqlNode node = new SqlNode("SELECT 1");
		assertTrue(node.getParameters().isEmpty());
		assertNull(node.getOffsetParameter());
		assertNull(node.getRowParameter());
		node.setParameters(null);
		assertTrue(node.clone().getParameters().isEmpty());
		assertNull(node.getCountSqlParameter());
	}
}
