/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class SqlServerPartitionFunctionMetadataTest {
	private ExResultSet boundary(Object id, Object value) {
		return new ExResultSet(null) {
			@Override
			public Object getObject(String name) {
				return name.equals("boundary_id") ? id : value;
			}
		};
	}

	@Test
	void emptyFunctionAndNullBoundaryRemainDistinct() throws Exception {
		var reader = new SqlServer2005PartitionFunctionReader(DialectHolder.defaultDialect2005);
		PartitionFunction function = new PartitionFunction("F").setDataType(DataType.INT);
		reader.addBoundary(function, boundary(null, null));
		assertTrue(function.getValues().isEmpty());
		reader.addBoundary(function, boundary(1, null));
		assertEquals(java.util.List.of("NULL"), function.getValues());
	}

	@Test
	void unicodeQuotesSpacesNumbersAndBinaryUseSharedLiterals() throws Exception {
		var reader = new SqlServer2005PartitionFunctionReader(DialectHolder.defaultDialect2005);
		PartitionFunction text = new PartitionFunction("F").setDataType(DataType.NVARCHAR).setLength(100);
		reader.addBoundary(text, boundary(1, "日本 O'Brien  "));
		assertEquals("N'日本 O''Brien  '", text.getValues().get(0));
		PartitionFunction binary = new PartitionFunction("B").setDataType(DataType.VARBINARY).setLength(4);
		reader.addBoundary(binary, boundary(1, new byte[] { 0, (byte) 255, 39 }));
		assertEquals("0x00ff27", binary.getValues().get(0).toLowerCase());
		PartitionFunction number = new PartitionFunction("N").setDataType(DataType.INT);
		reader.addBoundary(number, boundary(1, 123));
		assertEquals("123", number.getValues().get(0));
		PartitionFunction restored = SchemaUtils.readXml(new java.io.StringReader(text.asXml()));
		String ddl = DialectHolder.defaultDialect2005.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(ddl.contains("N'日本 O''Brien  '"), ddl);
	}

	@Test
	void queryRetainsEmptyFunctionsAndExposesBoundaryIdentity() throws Exception {
		try (var input = SqlServer2005PartitionFunctionReader.class.getResourceAsStream("partitionFunctions2005.sql")) {
			String sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(sql.contains("LEFT JOIN sys.partition_range_values"));
			assertTrue(sql.contains("prv.boundary_id"));
			assertTrue(sql.contains("pp.system_type_id = t.user_type_id"));
		}
	}

	@Test
	void decimalPrecisionUsesDeclaredDigitsInsteadOfStorageBytes() throws Exception {
		var reader = new SqlServer2005PartitionFunctionReader(DialectHolder.defaultDialect2005);
		var values = Map.<String, Object>of("partition_function_name", "F", "type_name", "decimal", "max_length", 17L,
				"precision", 30L, "scale", 8);
		PartitionFunction function = reader.createPartitionFunction(new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name) == null ? null : values.get(name).toString();
			}

			@Override
			public Object getObject(String name) {
				return values.get(name);
			}

			@Override
			public long getLong(String name) {
				return values.get(name) == null ? 0L : ((Number) values.get(name)).longValue();
			}

			@Override
			public boolean wasNull() {
				return false;
			}

			@Override
			public Long getLongValue(String name) {
				return values.get(name) == null ? null : ((Number) values.get(name)).longValue();
			}

			@Override
			public Integer getInteger(String name) {
				return values.get(name) == null ? null : ((Number) values.get(name)).intValue();
			}

			@Override
			public java.sql.Timestamp getTimestamp(String name) {
				return null;
			}

			@Override
			public boolean getBoolean(String name) {
				return false;
			}

			@Override
			public int getInt(String name) {
				return 1;
			}
		});
		assertEquals(30L, function.getLength());
		assertEquals(8, function.getScale());
		reader.addBoundary(function, boundary(1, new java.math.BigDecimal("123.45678901")));
		String ddl = DialectHolder.defaultDialect2005.createSqlFactoryRegistry().createSql(function, SqlType.CREATE)
				.get(0).getSqlText();
		assertTrue(ddl.contains("DECIMAL(30,8)"), ddl);
		assertTrue(ddl.contains("123.45678901"), ddl);
	}

	@Test
	void sqlNullAndTextNullRemainDistinctThroughXml() throws Exception {
		var reader = new SqlServer2005PartitionFunctionReader(DialectHolder.defaultDialect2005);
		PartitionFunction number = new PartitionFunction("F").setDataType(DataType.INT);
		reader.addBoundary(number, boundary(1, null));
		reader.addBoundary(number, boundary(2, 1));
		PartitionFunction restored = SchemaUtils.readXml(new java.io.StringReader(number.asXml()));
		assertTrue(restored.getValues().contains("NULL"));
		PartitionFunction text = new PartitionFunction("T").setDataType(DataType.NVARCHAR).setLength(20);
		reader.addBoundary(text, boundary(1, "NULL"));
		assertEquals("N'NULL'", text.getValues().get(0));
	}
}
