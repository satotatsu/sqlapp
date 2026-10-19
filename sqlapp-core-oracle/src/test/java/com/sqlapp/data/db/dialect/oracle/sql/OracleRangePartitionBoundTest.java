/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleRangePartitionBoundTest {
	@Test
	void markersAreTokensOutsideQuotesAndComments() {
		for (String text : List.of("MAXVALUE", " maxvalue ", "MAXVALUE, 'text'", "/* text */ MAXVALUE"))
			assertTrue(OracleRangePartitionBound.isUnbounded(text), text);
		for (String text : List.of("'MAXVALUE'", "N'MAXVALUE'", "'a''MAXVALUE'", "'MAXVALUE', 10", "10, MAXVALUE",
				"'MAXVALUE', MAXVALUE", "TO_DATE('MAXVALUE', 'YYYY')", "\"MAXVALUE\"", "MAXVALUE_SUFFIX",
				"PREFIX_MAXVALUE", "10 /* MAXVALUE */", "10 -- MAXVALUE\n, 20"))
			assertFalse(OracleRangePartitionBound.isUnbounded(text), text);
	}

	@Test
	void alternativeQuotesKeepApostrophesInsideTheLiteral() {
		assertTrue(OracleRangePartitionBound.isUnbounded("-- text\r MAXVALUE, 10"));
		for (String text : List.of("q'[MAXVALUE]'", "q'{a'MAXVALUE}'", "Q'<MAXVALUE>'", "q'(MAXVALUE)'",
				"q'!a'MAXVALUE!'", "nq'[MAXVALUE]'", "q'あMAXVALUEあ'", "q'\uD83D\uDE00MAXVALUE\uD83D\uDE00'")) {
			assertFalse(OracleRangePartitionBound.isUnbounded(text), text);
			assertFalse(OracleRangePartitionBound.isUnbounded(text + ", MAXVALUE"), text);
			assertTrue(OracleRangePartitionBound.isUnbounded("MAXVALUE, " + text), text);
		}
	}

	@Test
	void malformedQuotedTextIsRejected() {
		for (String text : List.of("'MAXVALUE", "\"MAXVALUE", "q'[MAXVALUE", "q'", "/* MAXVALUE", "MAXVALUE, '"))
			assertThrows(IllegalArgumentException.class, () -> OracleRangePartitionBound.isUnbounded(text), text);
	}
}
