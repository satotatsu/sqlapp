package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;

class SqlConditionalBranchesTest {

	@Test
	void evaluatesOnlyTheSelectedBranchIncludingNestedConditionsAndBinds() {
		SqlNode node = SqlParser.getInstance().parse(DialectResolver.getInstance().getDefaultDialect(),
				"SELECT /*if mode == 1*/ /*a*/0\n-- else if mode == 2 && fallback\n"
				+ "/*if nested*/ /*b*/0 /*end*/\n-- else\n /*c*/0\n/*end*/");
		for (int mode = 1; mode <= 3; mode++) {
			SqlParameterCollection result = node.eval(Map.of("mode", mode, "fallback", true, "nested", true, "a", 11, "b", 22, "c", 33));
			assertEquals("SELECT ?", result.getSql().trim().replaceAll("\\s+", " "));
			assertEquals(1, result.getParameterSize());
			assertEquals(mode * 11, result.getBindParameters().get(0).getBindParameter().getValue());
		}
		assertNotNull(node.getParameterByName("b"));
		assertNotNull(node.getParameterByName("c"));
		assertNotNull(node.getParameterByName("fallback"));
		IfNode original = (IfNode) node.getChildNodes().stream().filter(IfNode.class::isInstance).findFirst().orElseThrow();
		SqlNode copy = node.clone();
		IfNode cloned = (IfNode) copy.getChildNodes().stream().filter(IfNode.class::isInstance).findFirst().orElseThrow();
		assertNotSame(original.getElseIfNodes(), cloned.getElseIfNodes());
		assertSame(cloned, cloned.getElseIfNodes().get(0).getParent());
		assertSame(cloned, cloned.getElseNode().getParent());
		cloned.getElseNode().getChildNodes().clear();
		assertFalse(original.getElseNode().getChildNodes().isEmpty());
	}

	@Test
	void retainsInlineElseSql() {
		SqlNode node = SqlParser.getInstance().parse(null, "SELECT /*if flag*/ 1\n-- else 2\n/*end*/");
		assertEquals("SELECT 2", node.eval(Map.of("flag", false)).getSql().trim().replaceAll("\\s+", " "));
	}
}
