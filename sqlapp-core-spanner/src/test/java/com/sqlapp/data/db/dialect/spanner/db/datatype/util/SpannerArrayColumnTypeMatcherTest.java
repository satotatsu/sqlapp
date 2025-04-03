package com.sqlapp.data.db.dialect.spanner.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.SimpleColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class SpannerArrayColumnTypeMatcherTest {

	@Test
	void testBoolean() {
		ColumnTypeMatcher columnTypeNameMatcher = createMatcher("BOOL", "BOOLEAN");
		TypeInformation column = createCoumn("BOOL", columnTypeNameMatcher);
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getArrayDimension().isEmpty());
		//
		column = createCoumn("Array<BOOL>", columnTypeNameMatcher);
		assertTrue(column.getDataTypeName().isEmpty());
		assertEquals(1, column.getArrayDimension().getAsInt());
	}

	private ColumnTypeMatcher createMatcher(String... dataTypeName) {
		ColumnTypeMatcher internalMatcher = new SimpleColumnTypeMatcher(dataTypeName);
		return new SpannerArrayColumnTypeMatcher(internalMatcher);
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp.get();
	}

}
