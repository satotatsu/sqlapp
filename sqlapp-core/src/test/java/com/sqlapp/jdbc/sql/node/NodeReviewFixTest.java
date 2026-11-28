package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.CachedEvaluator;

/**
 * Regression tests for the node package review fixes.
 */
class NodeReviewFixTest {
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

	private List<Object> values(SqlParameterCollection parameters) {
		return parameters.getBindParameters().stream().flatMap(holder -> holder.stream())
				.map(BindParameter::getValue).toList();
	}

	// equals / hashCode

	@Test
	void commentNodeEqualsIsReflexive() {
		Node root = parse("SELECT * FROM t WHERE a = /*a*/1");
		Node node = root.getChildNodes().stream().filter(n -> n instanceof CommentNode).findFirst().get();
		assertEquals(node, node);
		assertEquals(node.hashCode(), node.hashCode());
	}

	@Test
	void separatelyParsedOrClonedNodesAreDistinctKeys() {
		Node first = parse("SELECT * FROM t WHERE a = /*a*/1");
		Node second = parse("SELECT * FROM t WHERE a = /*a*/1");
		Node clone = first.clone();
		assertNotEquals(first, second);
		assertNotEquals(first, clone);
		Set<Node> nodes = new HashSet<>();
		nodes.add(first.getChildNodes().get(1));
		nodes.add(second.getChildNodes().get(1));
		nodes.add(clone.getChildNodes().get(1));
		assertEquals(3, nodes.size());
	}

	// clone does not share bindParameter

	@Test
	void bindVariableNodeCloneDoesNotShareBindParameter() {
		BindVariableNode node = new BindVariableNode();
		node.setExpression("a");
		BindVariableNode clone = node.clone();
		assertNotSame(node.getBindParameter(), clone.getBindParameter());
		clone.setExpression("b");
		assertEquals("a", node.getBindParameter().getName());
		assertEquals("b", clone.getBindParameter().getName());
	}

	@Test
	void bindVariableArrayNodeCloneDoesNotShareBindParameter() {
		Node root = parse("SELECT * FROM t WHERE colA IN /*a*/(1)");
		BindVariableArrayNode node = (BindVariableArrayNode) root.getChildNodes().stream()
				.filter(n -> n instanceof BindVariableArrayNode).findFirst().get();
		BindVariableArrayNode clone = node.clone();
		clone.setExpression("b");
		assertEquals("a", node.getExpression());
		Map<String, Object> context = CommonUtils.map();
		context.put("a", List.of(1, 2));
		assertEquals(List.of(1, 2), values(root.eval(context)));
	}

	// BindVariableArrayNode reads each Row element

	@Test
	void arrayOfRowsReadsEachElementRow() {
		Node root = parse("SELECT * FROM t WHERE colA IN /*colA*/(1)");
		Map<String, Object> context = CommonUtils.map();
		context.put("colA", table(3).getRows());
		SqlParameterCollection parameters = root.eval(context);
		assertEquals(List.of(0, 1, 2), values(parameters));
		assertTrue(parameters.getSql().contains("(?,?,?)"), parameters.getSql());
	}

	@Test
	void arrayOfRowsFromParametersContextReadsEachElementRow() {
		Node root = parse("SELECT * FROM t WHERE colA IN /*colA*/(1)");
		ParametersContext context = new ParametersContext();
		context.put("colA", table(2).getRows());
		assertEquals(List.of(0, 1), values(root.eval(context)));
	}

	@Test
	void arrayOfRowsWithoutMatchingColumnIsRejected() {
		Node root = parse("SELECT * FROM t WHERE colA IN /*rows*/(1)");
		Map<String, Object> context = CommonUtils.map();
		context.put("rows", table(2).getRows());
		assertThrows(IllegalArgumentException.class, () -> root.eval(context));
	}

	// VALUES

	@Test
	void valuesWithoutRowsIsRejectedWithMessage() {
		Node root = parse("/*VALUES*/VALUES(/*colA*/0)/*END*/");
		Map<String, Object> context = CommonUtils.map();
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> root.eval(context));
		assertTrue(e.getMessage().contains("VALUES"), e.getMessage());
	}

	@Test
	void valuesRowTemplateIsEvaluatedOncePerRow() {
		Node root = parse("/*VALUES*/VALUES(/*colA*2*/0)/*END*/");
		BindVariableNode bind = (BindVariableNode) root.getChildNodes().get(0).getChildNodes().stream()
				.filter(n -> n instanceof BindVariableNode).findFirst().get();
		AtomicInteger count = new AtomicInteger();
		bind.setEvaluator(new CountingEvaluator(count));
		SqlParameterCollection parameters = root.eval(table(3));
		assertEquals(3, count.get());
		assertEquals(3, parameters.getParameterSize());
	}

	// ReplaceVariableNode

	@Test
	void replaceWithLengthKeepsTrailingTextWhenValueIsNull() {
		Node root = parse("SELECT * FROM /*$name;length=3*/abc_suffix");
		Map<String, Object> context = CommonUtils.map();
		context.put("name", null);
		assertEquals("SELECT * FROM _suffix", root.eval(context).getSql().trim());
		context.put("name", "xyz");
		assertEquals("SELECT * FROM xyz_suffix", root.eval(context).getSql().trim());
	}

	@Test
	void replaceLengthIsValidated() {
		ReplaceVariableNodeFactory factory = new ReplaceVariableNodeFactory();
		assertThrows(IllegalArgumentException.class, () -> factory.parseSql("/*$name;length=20*/abc"));
		assertThrows(IllegalArgumentException.class, () -> factory.parseSql("/*$name;length=x*/abc"));
		assertThrows(IllegalArgumentException.class, () -> factory.parseSql("/*$name;length=-1*/abc"));
	}

	// sanitize

	@Test
	void sanitizeDoesNotRebuildRemovedTokens() {
		assertEquals("", CommentNode.sanitize("//**"));
		assertEquals("", CommentNode.sanitize("**//"));
		assertEquals("", CommentNode.sanitize("-;-"));
		assertEquals("a''b", CommentNode.sanitize("a'b"));
		assertEquals("'a''b'", CommentNode.sanitize("'a'b'"));
	}

	@Test
	void sanitizeAcceptsSingleQuote() {
		assertEquals("''", CommentNode.sanitize("'"));
	}

	private static final class CountingEvaluator implements CachedEvaluator {
		private final AtomicInteger count;

		CountingEvaluator(AtomicInteger count) {
			this.count = count;
		}

		@SuppressWarnings("unchecked")
		private <T> T next() {
			return (T) Integer.valueOf(count.incrementAndGet());
		}

		@Override
		public <T> T eval(String expression, ParametersContext context) {
			return next();
		}

		@Override
		public <T> T eval(String expression, ParametersContext context, Class<T> clazz) {
			return next();
		}

		@Override
		public <T> T eval(String expression, Object context) {
			return next();
		}

		@Override
		public boolean evalBoolean(String expression, Object context) {
			return true;
		}

		@Override
		public <T> T eval(String expression, Map<?, ?> context) {
			return next();
		}

		@Override
		public boolean evalBoolean(String expression, Map<?, ?> context) {
			return true;
		}

		@Override
		public boolean evalBoolean(String expression, ParametersContext context) {
			return true;
		}
	}
}
