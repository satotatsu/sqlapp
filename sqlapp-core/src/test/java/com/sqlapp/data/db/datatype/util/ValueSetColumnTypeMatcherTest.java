package com.sqlapp.data.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ValueSetColumnTypeMatcherTest {

	@Test
	void test() {
		ColumnTypeMatcher columnTypeNameMatcher = new ValueSetColumnTypeMatcher("ENUM");
		TypeInformation column = createCoumn("ENUM(1,2,3,4)", columnTypeNameMatcher);
		assertEquals(4, column.getValues().get().size());
		int i = 0;
		assertEquals("1", column.getValues().get().get(i++));
		assertEquals("2", column.getValues().get().get(i++));
		assertEquals("3", column.getValues().get().get(i++));
		assertEquals("4", column.getValues().get().get(i++));
		assertEquals("ENUM", column.getDataTypeName().get());
		//
		column = createCoumn("ENUM('1','2')", columnTypeNameMatcher);
		assertEquals(2, column.getValues().get().size());
		i = 0;
		assertEquals("'1'", column.getValues().get().get(i++));
		assertEquals("'2'", column.getValues().get().get(i++));
		assertEquals("ENUM", column.getDataTypeName().get());
	}

	@Test
	void testSet() {
		ColumnTypeMatcher columnTypeNameMatcher = new ValueSetColumnTypeMatcher("SET");
		TypeInformation column = createCoumn("SET(1,2,3,4)", columnTypeNameMatcher);
		assertEquals(4, column.getValues().get().size());
		int i = 0;
		assertEquals("1", column.getValues().get().get(i++));
		assertEquals("2", column.getValues().get().get(i++));
		assertEquals("3", column.getValues().get().get(i++));
		assertEquals("4", column.getValues().get().get(i++));
		assertEquals("SET", column.getDataTypeName().get());
		//
		column = createCoumn("SET('1','2')", columnTypeNameMatcher);
		assertEquals(2, column.getValues().get().size());
		i = 0;
		assertEquals("'1'", column.getValues().get().get(i++));
		assertEquals("'2'", column.getValues().get().get(i++));
		assertEquals("SET", column.getDataTypeName().get());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
