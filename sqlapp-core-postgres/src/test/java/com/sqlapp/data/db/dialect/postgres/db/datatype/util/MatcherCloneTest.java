package com.sqlapp.data.db.dialect.postgres.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DecimalType;

class MatcherCloneTest {
	@Test
	void cloneRebindsDefaultsThroughNestedWrappers() {
		var original = new DecimalType().setDefaultPrecision(10).setDefaultScale(2);
		original.convertColumnTypeMatchers(m -> new PostgresArrayColumnTypeMatcher(new PostgresArrayColumnTypeMatcher(m)));
		var copy = original.clone().setDefaultPrecision(20).setDefaultScale(4);
		var info = copy.matchDataTypeName("DECIMAL").orElseThrow();
		assertEquals(20L, info.getLength().orElseThrow());
		assertEquals(4, info.getScale().orElseThrow());
		var before = original.matchDataTypeName("DECIMAL").orElseThrow();
		assertEquals(10L, before.getLength().orElseThrow());
		assertEquals(2, before.getScale().orElseThrow());
	}
}
