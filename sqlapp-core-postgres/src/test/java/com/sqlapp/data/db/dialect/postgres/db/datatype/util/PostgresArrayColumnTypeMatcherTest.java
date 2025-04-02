package com.sqlapp.data.db.dialect.postgres.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher.MatcherColumn;
import com.sqlapp.data.db.dialect.postgres.db.datatype.util.PostgresArrayColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class PostgresArrayColumnTypeMatcherTest {

	@Test
	void testChar() {
		ColumnTypeMatcher columnTypeNameMatcher = createMatcher(
				"(?<dataTypeName>char(acter)?|bpchar(acter)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		TypeInformation column = createCoumn("BPCHAR(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertEquals("BPCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("BPCHARACTER(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		assertEquals("BPCHARACTER", column.getDataTypeName().get());
		//
		column = createCoumn("CHARACTER( 41 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals("CHARACTER", column.getDataTypeName().get());
		//
		column = createCoumn("CHAR", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		//
		column = createCoumn("CHAR []", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(1, column.getArrayDimension().getAsInt());
		//
		column = createCoumn("CHAR [][]", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(2, column.getArrayDimension().getAsInt());
		//
		column = createCoumn("CHAR [  ] [  ] ", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(2, column.getArrayDimension().getAsInt());
		//
		column = createCoumn("CHAR [  ] [  ] [4]", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(3, column.getArrayDimension().getAsInt());
	}

	private ColumnTypeMatcher createMatcher(String pattern, MatcherColumn... matcherColumns) {
		ColumnTypeMatcher internalMatcher = new RegexColumnTypeMatcher(
				"(?<dataTypeName>char(acter)?|bpchar(acter)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		return new PostgresArrayColumnTypeMatcher(internalMatcher);
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp.get();
	}

}
