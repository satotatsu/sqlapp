package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.schemas.Column;

class DbDataTypeMatchingTest {
	@Test
	void regexConvenienceMethodsApplyCallbacksAndPreserveNames() {
		RangeType type = new RangeType();
		type.setPetternColumnTypeMatcher("(?<name>INT4RANGE)",
				(m, info) -> info.setDataTypeName(m.group("name")));
		type.addPetternColumnTypeMatcher("(?<name>DATERANGE)",
				(m, info) -> info.setDataTypeName(m.group("name")));
		for (String name : new String[] { "INT4RANGE", "DATERANGE" }) {
			Column column = new Column();
			type.matchDataTypeName(name).orElseThrow().set(column);
			assertEquals(DataType.RANGE, column.getDataType());
			assertEquals(name, column.getDataTypeName());
		}
		assertTrue(type.matchDataTypeName("RANGE").isEmpty());
	}

	@Test
	void explicitMatcherNameSurvivesAndMissingNameUsesRegisteredType() {
		RangeType type = new RangeType();
		type.setColumnTypeMatcher(new RegexColumnTypeMatcher("INT4RANGE",
				(m, info) -> info.setDataTypeName("INT4RANGE")));
		assertEquals("INT4RANGE", type.matchDataTypeName("INT4RANGE").orElseThrow().getDataTypeName().orElseThrow());
		type.setPetternColumnTypeMatcher("INT4RANGE");
		assertEquals("RANGE", type.matchDataTypeName("INT4RANGE").orElseThrow().getDataTypeName().orElseThrow());
	}

	@Test
	void explicitLengthDoesNotRequireMaximumAndDefaultsStillApply() {
		VarcharType type = new VarcharType();
		assertEquals(123L, type.getLength(123L));
		type.setDefaultLength(10L);
		assertEquals(10L, type.getLength(null));
		assertEquals(123L, type.getLength(123L));
		type.setMaxLength(100L);
		assertEquals(100L, type.getLength(123L));
		assertEquals(50L, type.getLength(50L));
		assertEquals("VARCHAR(50)", type.getColumCreateDefinition(type.getLength(50L), null));
	}
}
