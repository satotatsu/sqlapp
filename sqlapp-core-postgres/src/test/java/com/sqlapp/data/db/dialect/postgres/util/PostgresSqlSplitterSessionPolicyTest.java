/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.exceptions.InvalidTextException;

class PostgresSqlSplitterSessionPolicyTest {

	@Test
	void initialStandardStringsModeIsExplicitAndEscapeStringsRemainEscaped() {
		var splitter = new PostgresSqlSplitter(null);
		assertTrue(splitter.isStandardConformingStrings());
		assertEquals(2, splitter.parse("SELECT 'a\\';SELECT 2;").size());
		splitter.setStandardConformingStrings(false);
		for (int i = 0; i < 2; i++)
			assertEquals(2, splitter.parse("SELECT 'a\\'b;c';SELECT 2;").size());
		assertEquals(2, splitter.parse("SELECT E'a\\'b;c';SELECT 2;").size());
		splitter.setStandardConformingStrings(true);
		assertEquals(2, splitter.parse("SELECT E'a\\'b;c';SELECT 2;").size());
	}

	@Test
	void quotingChangesAndIndeterminateSetConfigCallsAreRejected() {
		for (String sql : new String[] { "SET standard_conforming_strings=off;SELECT 2;",
				"SET LOCAL standard_conforming_strings TO 'off';", "SET SESSION \"standard_conforming_strings\"=on",
				"RESET standard_conforming_strings;", "RESET ALL;", "DISCARD ALL;",
				"SELECT set_config('standard_conforming_strings','off',false);",
				"SELECT pg_catalog.\"set_config\"('standard_conforming_strings','off',true);",
				"SELECT set_config($1,'off',false);" }) {
			var failure = assertThrows(InvalidTextException.class, () -> new PostgresSqlSplitter(null).parse(sql), sql);
			assertTrue(failure.getMessage().contains("fixed standard_conforming_strings"));
		}
		for (String sql : new String[] { "SELECT 'SET standard_conforming_strings=off';",
				"SET application_name='test';", "SELECT foo$set_config(1);", "SELECT \"a set_config(\" FROM t;",
				"SELECT set_config('application_name','test',false);",
				"SELECT $$set_config('standard_conforming_strings','off',false)$$;",
				"-- SET standard_conforming_strings=off\nSELECT 1;" })
			assertFalse(new PostgresSqlSplitter(null).parse(sql).isEmpty());
	}

}
