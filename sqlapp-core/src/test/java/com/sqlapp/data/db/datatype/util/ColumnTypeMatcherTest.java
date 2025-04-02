package com.sqlapp.data.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ColumnTypeMatcherTest {

	@Test
	void test() {
		SimpleColumnTypeMatcher columnTypeNameMatcher = new SimpleColumnTypeMatcher("DOUBLE", "DOUBLE PRECISION");
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match("DOUBLE");
		assertTrue(columnOp.isPresent());
		columnOp = columnTypeNameMatcher.match("DOUBLE  PRECISION");
		assertTrue(columnOp.isPresent());
		columnOp = columnTypeNameMatcher.match("AAAA  PRECISION");
		assertTrue(columnOp.isEmpty());
	}

}
