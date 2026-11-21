/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class PhoenixEncodedBinaryTest {
	@Test
	void preservesEncodedTypeThroughMetadataMappingXmlDdlAndJdbc() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			final var column = new Column("PAYLOAD");
			assertTrue(dialect.setDbType(java.sql.Types.VARBINARY, "varbinary_encoded", null, null, column));
			assertEquals(DataType.VARBINARY, column.getDataType());
			assertEquals("VARBINARY_ENCODED", column.getDataTypeName());
			final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
			assertEquals("VARBINARY_ENCODED", dialect.createSqlBuilder().definition(restored).toString());
			assertEquals("VARBINARY", dialect.getDbDataTypes().getDbType(DataType.VARBINARY).getTypeName());
			final byte[] input = { 0, (byte) 255, 1, 0 };
			final var handler = dialect.getDbDataType(restored).getJdbcTypeHandler();
			final var rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { ResultSet.class }, (p, method, args) -> input);
			assertArrayEquals(input, (byte[]) handler.getObject(rs, 1));
			final Object[] bound = new Object[3];
			final var stmt = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (p, method, args) -> {
						if (method.getName().equals("setObject"))
							System.arraycopy(args, 0, bound, 0, 3);
						return null;
					});
			handler.setObject(stmt, 1, input, restored);
			assertArrayEquals(input, (byte[]) bound[1]);
			assertEquals(java.sql.Types.VARBINARY, bound[2]);
			assertEquals("X'00ff0100'", dialect.getSqlValueDefinition(restored, input));
			assertEquals("X''", dialect.getSqlValueDefinition(restored, new byte[0]));
			assertEquals("NULL", dialect.getSqlValueDefinition(restored, null));
			assertThrows(IllegalArgumentException.class, () -> dialect.getSqlValueDefinition(restored, "00ff"));
		}
	}

	@Test
	void respectsTheFiveThreeBoundaryAndRejectsEncodedArrays() {
		assertFalse(((Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 5, 2, 99)).supportsEncodedBinary());
		assertTrue(((Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 5, 3, 0)).supportsEncodedBinary());
		assertTrue(((Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 5, 3, null)).supportsEncodedBinary());
		assertFalse(DialectResolver.getInstance().getDialect("Phoenix", 5, 3, null).supportsValues());
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9)) {
			final var column = new Column("V").setDataType(DataType.VARBINARY).setDataTypeName("VARBINARY_ENCODED");
			assertThrows(UnsupportedOperationException.class,
					() -> dialect.setDbType("VARBINARY_ENCODED", null, null, new Column("V")));
			assertThrows(UnsupportedOperationException.class, () -> dialect.createSqlBuilder().definition(column));
		}
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect5_3
				.setDbType("VARBINARY_ENCODED ARRAY", null, null, new Column("V")));
	}
}
