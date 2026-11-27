package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.util.*;

class DatatypeResolutionRegressionTest {
	private static class TestDialect extends Dialect {
		TestDialect() { super(() -> null); }
	}

	@Test
	void metadataOverridesDefaultsButNotExplicitDeclarations() {
		var dialect = new TestDialect();
		dialect.getDbDataTypes().addDecimal(t -> t.setDefaultPrecision(38).setDefaultScale(0));
		var metadata = dialect.matchDbType("DECIMAL", 10L, 2).orElseThrow();
		assertEquals(10L, metadata.getLength().orElseThrow());
		assertEquals(2, metadata.getScale().orElseThrow());
		assertFalse(metadata.isSetDefaultLength());
		assertFalse(metadata.isSetDefaultScale());
		var explicit = dialect.matchDbType("DECIMAL(12,3)", 10L, 2).orElseThrow();
		assertEquals(12L, explicit.getLength().orElseThrow());
		assertEquals(3, explicit.getScale().orElseThrow());
		var partial = dialect.matchDbType("DECIMAL(12)", 10L, 2).orElseThrow();
		assertEquals(12L, partial.getLength().orElseThrow());
		assertEquals(2, partial.getScale().orElseThrow());
		var defaults = dialect.matchDbType("DECIMAL", null, null).orElseThrow();
		assertEquals(38L, defaults.getLength().orElseThrow());
		assertEquals(0, defaults.getScale().orElseThrow());
		dialect.getDbDataTypes().addVarchar(100, t -> t.setDefaultLength(1L));
		assertEquals(20L, dialect.matchDbType("VARCHAR", 20L, null).orElseThrow().getLength().orElseThrow());
	}

	@Test
	void promotionRetainsDeclaredLengthIncludingCachedCalls() {
		var types = new DbDataTypeCollection();
		types.addVarchar(10, t -> t.setDefaultLength(10L));
		types.addVarchar(100, t -> t.setDefaultLength(100L));
		for (int i = 0; i < 2; i++) {
			var info = types.matchTypeInformation("VARCHAR(20)", null).orElseThrow();
			assertEquals(20L, info.getLength().orElseThrow());
			assertEquals(100L, ((LengthProperties<?>) info.getDbDataType()).getMaxLength());
			assertEquals("VARCHAR(20)", info.getDbDataType().getColumCreateDefinition(info.getLength().orElseThrow(), null));
		}
	}

	@Test
	void copiesPreserveSubclassOverridesForEverySizedMatcher() {
		var matchers = new ColumnTypeMatcher[] {
			new LengthColumnTypeMatcher("VARCHAR") {
				@Override public Optional<TypeInformation> match(String text) { return Optional.empty(); }
			},
			new PrecisionColumnTypeMatcher("FLOAT") {
				@Override public Optional<TypeInformation> match(String text) { return Optional.empty(); }
			},
			new PrecisionScaleColumnTypeMatcher("DECIMAL") {
				@Override public Optional<TypeInformation> match(String text) { return Optional.empty(); }
			}
		};
		for (var matcher : matchers) {
			var copy = matcher.copy();
			assertNotSame(matcher, copy);
			assertEquals(matcher.getClass(), copy.getClass());
			for (String name : new String[] {"VARCHAR", "FLOAT", "DECIMAL"}) {
				assertTrue(copy.match(name).isEmpty());
			}
		}
	}

	@Test
	void clonedSubclassSuperMatchUsesCloneDefaults() {
		var original = new DecimalType().setDefaultPrecision(10).setDefaultScale(2);
		original.setColumnTypeMatcher(new PrecisionScaleColumnTypeMatcher("DECIMAL", "", "") {
			@Override public Optional<TypeInformation> match(String text) {
				return super.match(text).map(info -> { info.setDataTypeName("CUSTOM"); return info; });
			}
		});
		var copy = original.clone().setDefaultPrecision(20).setDefaultScale(4);
		var info = copy.matchDataTypeName("DECIMAL").orElseThrow();
		assertEquals("CUSTOM", info.getDataTypeName().orElseThrow());
		assertEquals(20L, info.getLength().orElseThrow());
		assertEquals(4, info.getScale().orElseThrow());
		assertEquals(10L, original.matchDataTypeName("DECIMAL").orElseThrow().getLength().orElseThrow());
	}

	@Test
	void prefixMatchersRebindDefaultsOnCloning() {
		var text = new VarcharType().setDefaultLength(10L);
		text.setColumnTypeMatcher(new LengthColumnTypeMatcher("VARCHAR", "SUFFIX"));
		assertEquals(20L, text.clone().setDefaultLength(20L)
				.matchDataTypeName("VARCHAR SUFFIX").orElseThrow().getLength().orElseThrow());
		assertEquals(10L, text.matchDataTypeName("VARCHAR SUFFIX").orElseThrow().getLength().orElseThrow());
		var number = new FloatType().setDefaultPrecision(10);
		number.setColumnTypeMatcher(new PrecisionColumnTypeMatcher("FLOAT", "SUFFIX"));
		assertEquals(20L, number.clone().setDefaultPrecision(20)
				.matchDataTypeName("FLOAT SUFFIX").orElseThrow().getLength().orElseThrow());
		assertEquals(10L, number.matchDataTypeName("FLOAT SUFFIX").orElseThrow().getLength().orElseThrow());
	}

	@Test
	void standardIntervalMetadataOverridesOnlyOmittedParameters() {
		var dialect = new TestDialect();
		dialect.getDbDataTypes().addStandardInterval(DataType.INTERVAL_DAY_TO_SECOND, 9, 9);
		var metadata = dialect.matchDbType("INTERVAL DAY TO SECOND", 3L, 4).orElseThrow();
		assertEquals(3L, metadata.getLength().orElseThrow());
		assertEquals(4, metadata.getScale().orElseThrow());
		var explicit = dialect.matchDbType("INTERVAL DAY(5) TO SECOND(7)", 3L, 4).orElseThrow();
		assertEquals(5L, explicit.getLength().orElseThrow());
		assertEquals(7, explicit.getScale().orElseThrow());
	}
}
