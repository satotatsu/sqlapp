package com.sqlapp.jdbc.sql.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.exceptions.SqlSecurityException;
import com.sqlapp.jdbc.sql.SqlParser;
import com.sqlapp.util.CommonUtils;

/**
 * Output specification of {@code /*#...*\/}.
 */
class OutputVariableNodeTest {
	private final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	private String sql(String sql, String name, Object value) {
		Map<String, Object> context = CommonUtils.map();
		context.put(name, value);
		return SqlParser.getInstance().parse(dialect, sql).eval(context).getSql().trim();
	}

	private OutputVariableNode node(String sql) {
		List<OutputVariableNode> nodes = new ArrayList<>(new OutputVariableNodeFactory().parseSql(sql).values());
		assertEquals(1, nodes.size());
		return nodes.get(0);
	}

	// default

	@Test
	void numbersAndBooleansAreOutput() {
		assertEquals("OFFSET 3 ROWS", sql("OFFSET /*# _offset */ ROWS", "_offset", 3));
		assertEquals("SELECT -1", sql("SELECT /*#v*/", "v", -1));
		assertEquals("SELECT 1.50", sql("SELECT /*#v*/", "v", new BigDecimal("1.50")));
		assertEquals("SELECT 1", sql("SELECT /*#v*/", "v", Boolean.TRUE));
	}

	@Test
	void identifierFragmentsAreOutput() {
		assertEquals("use schema1_dev", sql("use schema1/*#suffix*/", "suffix", "_dev"));
		assertEquals("use schema1", sql("use schema1/*#suffix*/", "suffix", ""));
		assertEquals("create s1.table t", sql("create /*#schemaName*/table t", "schemaName", "s1."));
		assertEquals("SELECT * FROM 顧客_1", sql("SELECT * FROM /*#t*/", "t", "顧客_1"));
	}

	@Test
	void nullOutputsNothing() {
		assertEquals("use schema1", sql("use schema1/*#suffix*/", "suffix", null));
	}

	@Test
	void unsafeTextIsRejected() {
		for (Object value : new Object[] { "x' OR '1'='1", "a b", "a--", "-;-", "1;DROP TABLE t", "a/*", "$$",
				LocalDate.of(2026, 1, 1) }) {
			assertThrows(SqlSecurityException.class, () -> sql("SELECT /*#v*/", "v", value), String.valueOf(value));
		}
	}

	// ;literal

	@Test
	void literalQuotesAndEscapes() {
		assertEquals("SELECT 'O''Reilly'", sql("SELECT /*#v;literal*/", "v", "O'Reilly"));
		assertEquals("SELECT ''''", sql("SELECT /*#v;literal*/", "v", "'"));
		assertEquals("SELECT 'a b; -- c'", sql("SELECT /*#v;literal*/", "v", "a b; -- c"));
		assertEquals("SELECT '1'", sql("SELECT /*#v;literal*/", "v", 1));
	}

	@Test
	void literalRejectsBackslash() {
		assertThrows(SqlSecurityException.class, () -> sql("SELECT /*#v;literal*/", "v", "a\\' OR 1=1 --"));
	}

	@Test
	void literalWithoutDialectRejectsBackslash() {
		OutputVariableNode node = node("SELECT /*#v;literal*/");
		assertEquals("'a''b'", node.toSql("a'b", null));
		assertThrows(SqlSecurityException.class, () -> node.toSql("a\\b", null));
	}

	// ;identifier

	@Test
	void identifierIsQuotedAsAWhole() {
		assertEquals("SELECT * FROM \"my table\"", sql("SELECT * FROM /*#t;identifier*/", "t", "my table"));
		assertEquals("SELECT * FROM \"a\"\"b\"", sql("SELECT * FROM /*#t;identifier*/", "t", "a\"b"));
		// A value that already looks quoted is still escaped as one identifier.
		assertEquals("SELECT * FROM \"\"\"x\"\"; DROP TABLE t; --\"",
				sql("SELECT * FROM /*#t;identifier*/", "t", "\"x\"; DROP TABLE t; --"));
		assertEquals("SELECT * FROM", sql("SELECT * FROM /*#t;identifier*/", "t", ""));
		assertThrows(SqlSecurityException.class, () -> sql("SELECT * FROM /*#t;identifier*/", "t", "a\0b"));
	}

	@Test
	void identifierWithoutDialectUsesDoubleQuotes() {
		assertEquals("\"a\"\"b\"", node("SELECT /*#v;identifier*/").toSql("a\"b", null));
	}

	// ;raw

	@Test
	void rawIsOutputAsIs() {
		assertEquals("ORDER BY a DESC, b", sql("ORDER BY /*#order;raw*/", "order", "a DESC, b"));
	}

	// option parsing

	@Test
	void optionsAreParsedFromTheExpression() {
		OutputVariableNode node = node("SELECT /*# v ; LITERAL */");
		assertEquals("v", node.getExpression());
		assertEquals(OutputVariableNode.OutputMode.LITERAL, node.getOutputMode());
		assertEquals(OutputVariableNode.OutputMode.DEFAULT, node("SELECT /*#v*/").getOutputMode());
		assertEquals(OutputVariableNode.OutputMode.RAW, node("SELECT /*#v;raw*/").getOutputMode());
		assertEquals(OutputVariableNode.OutputMode.IDENTIFIER, node("SELECT /*#v;Identifier*/").getOutputMode());
	}

	@Test
	void unknownOrConflictingOptionsAreRejected() {
		OutputVariableNodeFactory factory = new OutputVariableNodeFactory();
		assertThrows(IllegalArgumentException.class, () -> factory.parseSql("SELECT /*#v;quoted*/"));
		assertThrows(IllegalArgumentException.class, () -> factory.parseSql("SELECT /*#v;literal;raw*/"));
	}
}
