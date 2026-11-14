/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;

/**
 * Splits a DuckDB script whose literals and comments contain semicolons and
 * executes the parts.
 */
class DuckDbSqlSplitterTest {

	private static final String SCRIPT = """
			/* setup; with /* nested; */ comment */
			CREATE TABLE t (v VARCHAR);
			-- line comment; ignored
			INSERT INTO t VALUES ('a;b'), (E'c\\';d'), ($$e;f$$), ($tag$g;'h$tag$), ('i\\');
			CREATE MACRO m(x) AS x || ';';
			SELECT count(*) FROM t;
			""";

	@Test
	void keepsDuckDbLiteralsAndCommentsIntact() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
				Statement statement = connection.createStatement()) {
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(DuckDbSqlSplitter.class, dialect.createSqlSplitter());
			final List<String> statements = new ArrayList<>();
			for (final SplitResult result : dialect.createSqlSplitter().parse(SCRIPT)) {
				if (!result.getTextType().isComment() && !result.getText().isBlank()) {
					statements.add(result.getText());
				}
			}
			assertEquals(4, statements.size(), statements.toString());
			for (final String sql : statements) {
				statement.execute(sql);
			}
			final List<String> values = new ArrayList<>();
			try (ResultSet rs = statement.executeQuery("SELECT m(v) FROM t ORDER BY rowid")) {
				while (rs.next()) {
					values.add(rs.getString(1));
				}
			}
			assertEquals(List.of("a;b;", "c';d;", "e;f;", "g;'h;", "i\\;"), values);
			assertTrue(statements.get(0).startsWith("CREATE TABLE") || statements.get(0).contains("CREATE TABLE"));
		}
	}
}
