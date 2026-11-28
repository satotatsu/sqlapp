package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class DatatypeApiRegressionTest {
	@Test
	void stringLengthPreservesExplicitValuesAndUsesDefaultsOnlyForOmissions() {
		var info = new TypeInformation();
		info.setDbDataType(new VarcharType().setDefaultLength(10L).setMaxLength(100L));
		info.setLength("20", null);
		assertEquals(20L, info.getLength().orElseThrow());
		assertFalse(info.isSetDefaultLength());
		info.setLength("200", "");
		assertEquals(200L, info.getLength().orElseThrow());
		info.setLength(null, null);
		assertEquals(10L, info.getLength().orElseThrow());
		assertTrue(info.isSetDefaultLength());
		info.setDbDataType(new DecimalType().setDefaultPrecision(38));
		info.setLength("10", null);
		assertEquals(10L, info.getLength().orElseThrow());
		info.setLength(null, null);
		assertEquals(38L, info.getLength().orElseThrow());
	}

	@Test
	void expandedUnitsAreNotCappedByDefaultLength() {
		var info = new TypeInformation();
		info.setDbDataType(new VarcharType().setDefaultLength(10L));
		for (String unit : new String[] {"K", "m", "G"}) {
			info.setLength("2", unit);
			long multiplier = unit.equals("K") ? 1024L : unit.equals("m") ? 1024L * 1024 : 1024L * 1024 * 1024;
			assertEquals(2 * multiplier, info.getLength().orElseThrow());
		}
		info.setDbDataType(new IntType());
		info.setLength("20", null);
		assertEquals(java.util.Optional.empty(), info.getLength());
	}

	@Test
	void customDecimalNameIsConsistentForDdlAndMatcherFallback() {
		var type = new DecimalType("MY_DECIMAL");
		assertEquals("MY_DECIMAL", type.getTypeName());
		assertEquals("MY_DECIMAL(10,2)", type.getColumCreateDefinition(10L, 2));
		type.setColumnTypeMatcher(new RegexColumnTypeMatcher("MY_DECIMAL"));
		assertEquals("MY_DECIMAL", type.matchDataTypeName("MY_DECIMAL").orElseThrow().getDataTypeName().orElseThrow());
		var types = new DbDataTypeCollection();
		types.addDecimal("NUMBER", t -> {});
		types.addNumeric("CUSTOM_NUMERIC", t -> {});
		assertEquals("NUMBER", types.getDbType(DataType.DECIMAL).getTypeName());
		assertEquals("CUSTOM_NUMERIC", types.getDbType(DataType.NUMERIC).getTypeName());
	}

	@Test
	void varcharXmlBindsValuesAndNullAsVarchar() throws Exception {
		var calls = new ArrayList<List<Object>>();
		PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
					if (method.getName().equals("setObject") || method.getName().equals("setNull")) {
						calls.add(List.of(method.getName(), List.of(args)));
					}
					return null;
				});
		var type = new SqlXmlType();
		type.setAsVarchar().getJdbcTypeHandler().setObject(statement, 1, "<a/>");
		type.getJdbcTypeHandler().setObject(statement, 2, null);
		assertEquals(List.of(List.of("setObject", List.of(1, "<a/>", Types.VARCHAR)),
				List.of("setNull", List.of(2, Types.VARCHAR))), calls);
		assertEquals(DataType.SQLXML, type.getDataType());
	}
}
