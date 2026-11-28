/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.WriterSqlExecutor;

/** PSQL written as an isql script restores through SET TERM. */
class FirebirdSqlScriptTest {
	private static final String PROCEDURE = "CREATE PROCEDURE P\nAS\nBEGIN\n  SUSPEND;\nEND";
	private static final String SELECT = "SELECT 1 FROM RDB$DATABASE";

	private static SqlOperation operation(final Dialect dialect, final String sql) {
		final SqlOperation operation = new SqlOperation(sql, SqlType.CREATE);
		dialect.setChangeAndResetSqlDelimiter(sql, operation);
		return operation;
	}

	@Test
	void psqlUsesSetTermAndItsTerminator() throws Exception {
		final Dialect dialect = DialectHolder.defaultDialect30;
		final StringWriter writer = new StringWriter();
		new WriterSqlExecutor(writer).execute(operation(dialect, PROCEDURE), operation(dialect, SELECT));
		assertEquals("SET TERM @ ;\n" + PROCEDURE + "\n@\nSET TERM ; @\n\n" + SELECT + ";\n\n", writer.toString());
	}

	@Test
	void writtenScriptSplitsBackIntoTheSameStatements() throws Exception {
		final Dialect dialect = DialectHolder.defaultDialect30;
		final StringWriter writer = new StringWriter();
		new WriterSqlExecutor(writer).execute(operation(dialect, PROCEDURE), operation(dialect, SELECT));
		final List<String> statements = dialect.createSqlSplitter().parse(writer.toString()).stream()
				.filter(part -> !part.getTextType().isComment()).map(SplitResult::getText).map(String::strip)
				.filter(text -> !text.isEmpty()).toList();
		assertEquals(List.of(PROCEDURE, SELECT), statements);
	}

	@Test
	void dialectTextSplitsBackIntoTheSameStatements() {
		final Dialect dialect = DialectHolder.defaultDialect30;
		final String text = dialect.toTextFromSqlOperation(
				List.of(new SqlOperation(PROCEDURE, SqlType.CREATE), new SqlOperation(SELECT, SqlType.SELECT)));
		assertEquals("SET TERM @ ;\n" + PROCEDURE + "\n@\nSET TERM ; @\n" + SELECT + "\n;\n", text);
		final List<String> statements = dialect.createSqlSplitter().parse(text).stream()
				.filter(part -> !part.getTextType().isComment()).map(SplitResult::getText).map(String::strip)
				.filter(value -> !value.isEmpty()).toList();
		assertEquals(List.of(PROCEDURE, SELECT), statements);
	}
}
