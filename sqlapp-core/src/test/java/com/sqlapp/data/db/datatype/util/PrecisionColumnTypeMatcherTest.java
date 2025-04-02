package com.sqlapp.data.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PrecisionColumnTypeMatcherTest {

	@Test
	void test() {
		PrecisionColumnTypeMatcher columnTypeNameMatcher = new PrecisionColumnTypeMatcher("DECIMAL");
		TypeInformation column = createCoumn("DECIMAL(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		//
		column = createCoumn("DECIMAL(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		//
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getScale().isEmpty());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 15);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(15, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 16);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(16, column.getLength().get().intValue());
		//
		columnTypeNameMatcher = new PrecisionColumnTypeMatcher("DECIMAL");
		assertTrue(columnTypeNameMatcher.match("DECIMAL( 41,2 )").isEmpty());
	}

	@Test
	void test2() {
		PrecisionColumnTypeMatcher columnTypeNameMatcher = new PrecisionColumnTypeMatcher("INTERVAL\\s+HOUR",
				"TO\\sSECOND");
		TypeInformation column = createCoumn("Interval hour(3) to second", columnTypeNameMatcher);
		assertEquals(3, column.getLength().get().intValue());
		column = createCoumn("INTERVAL hour to second", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
