package com.sqlapp.data.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PrecisionScaleColumnTypeMatcherTest {

	@Test
	void test() {
		PrecisionScaleColumnTypeMatcher columnTypeNameMatcher = new PrecisionScaleColumnTypeMatcher("DECIMAL");
		TypeInformation column = createCoumn("DECIMAL(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL( 41,2 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals(2, column.getScale().get().intValue());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 15);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(15, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 16);
		columnTypeNameMatcher.setDefaultScale(() -> 2);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(16, column.getLength().get().intValue());
		assertEquals(2, column.getScale().get().intValue());
		assertEquals("DECIMAL", column.getDataTypeName().get());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
