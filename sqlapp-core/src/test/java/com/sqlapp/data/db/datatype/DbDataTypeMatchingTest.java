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
		type.setPetternColumnTypeMatcher("(?<name>INT4RANGE)", (m, info) -> info.setDataTypeName(m.group("name")));
		type.addPetternColumnTypeMatcher("(?<name>DATERANGE)", (m, info) -> info.setDataTypeName(m.group("name")));
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
		type.setColumnTypeMatcher(
				new RegexColumnTypeMatcher("INT4RANGE", (m, info) -> info.setDataTypeName("INT4RANGE")));
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
	@Test
	void repeatedOverflowResolutionUsesSourceMatcherAndReturnsFreshInformation() {
		DbDataTypeCollection types = new DbDataTypeCollection();
		types.addVarchar(10);
		types.addClob("CLOB", 1000L);
		for (Long length : new Long[] {20L, null}) {
			var first = types.matchTypeInformation("VARCHAR(20)", length).orElseThrow();
			assertEquals(DataType.CLOB, first.getDataType().orElseThrow());
			var expectedLength = first.getLength();
			first.setLength(1L);
			first.setDataTypeName("CHANGED");
			var second = types.matchTypeInformation("VARCHAR(20)", length).orElseThrow();
			assertEquals(DataType.CLOB, second.getDataType().orElseThrow());
			assertEquals("VARCHAR", second.getDataTypeName().orElseThrow());
			assertEquals(expectedLength, second.getLength());
		}
		assertEquals(DataType.VARCHAR,
				types.matchTypeInformation("VARCHAR(5)", 5L).orElseThrow().getDataType().orElseThrow());
	}

	@Test
	void clonedLengthMatcherUsesIndependentDefaultsAndCollections() {
		VarcharType original = new VarcharType().setDefaultLength(10L);
		VarcharType copy = original.clone().setDefaultLength(20L);
		assertEquals(20L, copy.matchDataTypeName("VARCHAR").orElseThrow().getLength().orElseThrow());
		assertEquals(10L, original.matchDataTypeName("VARCHAR").orElseThrow().getLength().orElseThrow());
		copy.setSizeSarrogation(5L, DataType.CLOB);
		assertEquals(null, original.getSizeSarrogation(5L));
		copy.clearColumnTypeMatchers();
		assertTrue(original.matchDataTypeName("VARCHAR").isPresent());
	}

	@Test
	void clonedNumericMatchersUseIndependentPrecisionAndScaleDefaults() {
		DecimalType original = new DecimalType().setDefaultPrecision(10).setDefaultScale(2);
		DecimalType copy = original.clone().setDefaultPrecision(20).setDefaultScale(4);
		var info = copy.matchDataTypeName("DECIMAL").orElseThrow();
		assertEquals(20L, info.getLength().orElseThrow());
		assertEquals(4, info.getScale().orElseThrow());
		assertEquals(2, original.matchDataTypeName("DECIMAL").orElseThrow().getScale().orElseThrow());
		FloatType floatType = new FloatType().setDefaultPrecision(10);
		assertEquals(20L, floatType.clone().setDefaultPrecision(20)
				.matchDataTypeName("FLOAT").orElseThrow().getLength().orElseThrow());
		assertEquals(10L, floatType.matchDataTypeName("FLOAT").orElseThrow().getLength().orElseThrow());
	}
}
