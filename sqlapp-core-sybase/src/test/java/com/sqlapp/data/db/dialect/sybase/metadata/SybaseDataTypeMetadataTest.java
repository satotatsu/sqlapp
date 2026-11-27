package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;

class SybaseDataTypeMetadataTest {
	@Test
	void keepsFloatStorageWidthSeparateFromBinaryPrecision() throws Exception {
		assertEquals(24L, SybaseUtils.getTypeLength("float", 8L, 24L));
		for (long bytes : new long[] { 4, 8 }) {
			var values = new HashMap<String, Object>();
			values.put("type_name", "float");
			values.put("column_name", "value");
			values.put("max_length", bytes);
			values.put("precision", 0L);
			var column = new SybaseColumnReader(DialectHolder.defaultDialect)
					.createColumn(SybaseReaderTestSupport.row(values));
			assertEquals(DataType.FLOAT, column.getDataType());
			assertEquals(bytes == 4 ? 15L : 48L, column.getLength());
			assertEquals(bytes == 4 ? "FLOAT(15)" : "FLOAT(48)",
					DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}

	@Test
	void unicodeMetadataUsesCharacterCountsAndPreservesTheNativeType() throws Exception {
		for (String name : new String[] { "unichar", "univarchar" }) {
			var values = new HashMap<String, Object>();
			values.put("type_name", name);
			values.put("column_name", "value");
			values.put("max_length", 24L);
			var column = new SybaseColumnReader(DialectHolder.defaultDialect)
					.createColumn(SybaseReaderTestSupport.row(values));
			assertEquals(12L, column.getLength());
			assertEquals(name.toUpperCase(java.util.Locale.ROOT) + "(12)",
					DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}

	@Test
	void lobLocatorLengthDoesNotLimitTheContentCapacity() throws Exception {
		for (String name : new String[] { "text", "unitext", "image" }) {
			assertNull(SybaseUtils.getTypeLength(name, 16L, 0L));
			var values = new HashMap<String, Object>();
			values.put("type_name", name);
			values.put("column_name", "value");
			values.put("max_length", 16L);
			var column = new SybaseColumnReader(DialectHolder.defaultDialect)
					.createColumn(SybaseReaderTestSupport.row(values));
			assertNotEquals(DataType.OTHER, column.getDataType());
			assertTrue(column.getLength() == null || column.getLength() > 16);
			assertEquals(name.toUpperCase(java.util.Locale.ROOT),
					DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}

	@Test
	void nationalCharacterMetadataUsesTheServerWidth() throws Exception {
		for (String name : new String[] { "nchar", "nvarchar" }) {
			assertThrows(IllegalArgumentException.class, () -> SybaseUtils.getMaxLength(name, 24L));
			for (int width : new int[] { 1, 2, 3, 4 }) {
				var values = new HashMap<String, Object>();
				values.put("type_name", name);
				values.put("column_name", "value");
				values.put("max_length", 12L * width);
				values.put("nchar_size", width);
				var column = new SybaseColumnReader(DialectHolder.defaultDialect)
						.createColumn(SybaseReaderTestSupport.row(values));
				assertEquals(12L, column.getLength());
			}
		}
	}
}
