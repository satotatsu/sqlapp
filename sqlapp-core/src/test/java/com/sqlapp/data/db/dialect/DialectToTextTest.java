/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.db.dialect.util.SqlSplitter.TextType;
import com.sqlapp.data.db.dialect.util.SqlTerminator;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;

/**
 * {@link Dialect#toText(java.util.Collection)} and
 * {@link Dialect#toTextFromSqlOperation(java.util.Collection)} render a script
 * that the dialect's splitter can split back into the same statements.
 */
class DialectToTextTest {

	private static final Dialect PLAIN = new Dialect(() -> null) {
		private static final long serialVersionUID = 1L;
	};

	/** Switches the delimiter for statements containing ';', like MySQL. */
	private static final Dialect SWITCHING = new Dialect(() -> null) {
		private static final long serialVersionUID = 1L;

		@Override
		public void setChangeAndResetSqlDelimiter(String sql, SqlTerminator terminator) {
			if (!sql.contains(";"))
				return;
			terminator.setStartStatementTerminator("delimiter $$");
			terminator.setTerminator("$$");
			terminator.setEndStatementTerminator("delimiter ;");
		}
	};

	/** Uses the same text for terminator and end, like Oracle's '/'. */
	private static final Dialect SLASH = new Dialect(() -> null) {
		private static final long serialVersionUID = 1L;

		@Override
		public void setChangeAndResetSqlDelimiter(String sql, SqlTerminator terminator) {
			if (!sql.contains(";"))
				return;
			terminator.setTerminator("/");
			terminator.setEndStatementTerminator("/");
		}
	};

	@Test
	void emptyInputRendersEmptyText() {
		assertEquals("", PLAIN.toTextFromSqlOperation(List.of()));
		assertEquals("", PLAIN.toText(List.<SplitResult>of()));
	}

	@Test
	void plainStatementsAreTerminatedWithSemicolons() {
		var text = PLAIN.toTextFromSqlOperation(List.of(new SqlOperation("INSERT INTO A VALUES(1)", SqlType.INSERT),
				new SqlOperation("INSERT INTO A VALUES(2)", SqlType.INSERT)));
		assertEquals("INSERT INTO A VALUES(1)\n;\nINSERT INTO A VALUES(2)\n;\n", text);
	}

	@Test
	void singleStatementIsTerminatedToo() {
		assertEquals("DELETE FROM A\n;\n",
				PLAIN.toTextFromSqlOperation(List.of(new SqlOperation("DELETE FROM A", SqlType.DELETE))));
	}

	@Test
	void statementAlreadyEndingWithSemicolonIsNotTerminatedTwice() {
		assertEquals("DELETE FROM A;\n",
				PLAIN.toTextFromSqlOperation(List.of(new SqlOperation("DELETE FROM A;", SqlType.DELETE))));
	}

	@Test
	void commentsAndBlankLinesAreNotTerminated() {
		var text = PLAIN.toTextFromSqlOperation(List.of(new SqlOperation("-- note", SqlType.COMMENT),
				new SqlOperation("", SqlType.EMPTY_LINE), new SqlOperation("DELETE FROM A", SqlType.DELETE)));
		assertEquals("-- note\n\nDELETE FROM A\n;\n", text);
	}

	@Test
	void nullTextIsSkipped() {
		assertEquals("DELETE FROM A\n;\n", PLAIN.toTextFromSqlOperation(
				java.util.Arrays.asList(new SqlOperation(null, SqlType.DELETE), new SqlOperation("DELETE FROM A",
						SqlType.DELETE))));
	}

	@Test
	void delimiterSwitchWrapsTheBodyAndTerminatesIt() {
		var text = SWITCHING.toTextFromSqlOperation(List.of(
				new SqlOperation("CREATE PROCEDURE P() BEGIN SELECT 1; END", SqlType.CREATE),
				new SqlOperation("SELECT 2", SqlType.SELECT)));
		assertEquals("delimiter $$\nCREATE PROCEDURE P() BEGIN SELECT 1; END\n$$\ndelimiter ;\nSELECT 2\n;\n", text);
	}

	@Test
	void identicalTerminatorAndEndAreWrittenOnce() {
		var text = SLASH.toTextFromSqlOperation(
				List.of(new SqlOperation("BEGIN NULL; END;", SqlType.CREATE), new SqlOperation("SELECT 1 FROM DUAL",
						SqlType.SELECT)));
		assertEquals("BEGIN NULL; END;\n/\nSELECT 1 FROM DUAL\n;\n", text);
	}

	@Test
	void splitResultsUseTheSameRendering() {
		var text = PLAIN.toText(List.of(new SplitResult(TextType.COMMENT, "-- c"),
				new SplitResult(TextType.SQL, "UPDATE A SET X=1"), new SplitResult(TextType.SQL, "  ")));
		assertEquals("-- c\nUPDATE A SET X=1\n;\n  \n", text);
	}
}
