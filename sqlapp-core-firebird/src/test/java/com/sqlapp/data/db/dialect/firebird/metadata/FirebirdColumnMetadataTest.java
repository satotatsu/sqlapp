/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

class FirebirdColumnMetadataTest {
	@Test
	void combinesColumnAndDomainNullabilityWithoutReversingTheFlag() throws Exception {
		for (int columnFlag : new int[] { 0, 1 }) {
			for (int domainFlag : new int[] { 0, 1 }) {
				var values = values();
				values.put("NULL_FLAG", columnFlag);
				values.put("SOURCE_NULL_FLAG", domainFlag);
				assertEquals(columnFlag == 1 || domainFlag == 1, read(values).isNotNull());
			}
		}
	}

	@Test
	void preservesCharacterLengthSeparatelyFromByteLengthAndReadsRemarksAndDefaults() throws Exception {
		var values = values();
		values.put("FIELD_TYPE", 37);
		values.put("FIELD_LENGTH", 40);
		values.put("CHAR_LEN", 10);
		values.put("REMARKS", "customer name");
		values.put("DEFAULT_SOURCE", "DEFAULT 'unknown'");
		Column column = read(values);
		assertEquals(10L, column.getLength());
		assertEquals(40L, column.getOctetLength());
		assertEquals("customer name", column.getRemarks());
		assertEquals("'unknown'", column.getDefaultValue());
	}

	@Test
	void keepsNamedDomainInheritanceSeparateFromColumnOverrides() throws Exception {
		var values = values();
		values.put("DOMAIN_NAME", "positive_int");
		values.put("SOURCE_NULL_FLAG", 1);
		values.put("DOMAIN_DEFAULT_SOURCE", "DEFAULT 3");
		values.put("DEFAULT_SOURCE", "DEFAULT 3");
		values.put("CHECK_CONDITION", "CHECK (VALUE>0)");
		Column inherited = read(values);
		assertEquals("positive_int", inherited.getDomainName());
		assertNull(inherited.getDefaultValue());
		assertFalse(inherited.isNotNull());
		assertTrue(inherited.getDomain().isNotNull());
		assertEquals("3", inherited.getDomain().getDefaultValue());
		assertNull(inherited.getCheckConstraint());
		values.put("NULL_FLAG", 1);
		values.put("COLUMN_DEFAULT_SOURCE", "DEFAULT 9");
		Column overridden = read(values);
		assertTrue(overridden.isNotNull());
		assertEquals("9", overridden.getDefaultValue());
		values.put("DOMAIN_NAME", "RDB$123");
		assertNull(read(values).getDomain());
	}

	private Map<String, Object> values() {
		var values = new java.util.TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
		values.put("COLUMN_NAME", "value");
		values.put("TABLE_NAME", "orders");
		values.put("FIELD_TYPE", 8);
		values.put("FIELD_LENGTH", 4);
		return values;
	}

	private Column read(Map<String, Object> values) throws Exception {
		boolean[] wasNull = { false };
		ResultSet rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					if (method.getName().equals("wasNull"))
						return wasNull[0];
					Object value = values.get(args[0]);
					wasNull[0] = value == null;
					return switch (method.getName()) {
					case "getString" -> value == null ? null : value.toString();
					case "getInt" -> value == null ? 0 : ((Number) value).intValue();
					case "getShort" -> value == null ? (short) 0 : ((Number) value).shortValue();
					case "getObject" -> value;
					default -> throw new UnsupportedOperationException(method.getName());
					};
				});
		var result = new ExResultSet(rows) {
			@Override
			public boolean contains(String name) {
				return true;
			}
		};
		result.setCheckColumns(false);
		return new FirebirdColumnReader(DialectHolder.defaultDialect25).createColumn(result);
	}
}
