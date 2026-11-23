/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.schemas.Column;

/** CHARACTER SET OCTETS text types are read and recreated as binary. */
class FirebirdBinaryMetadataTest {

	private static Column read(final int type, final int length, final String collation) {
		final Column column = new Column("V").setCharacterSet("OCTETS  ").setCollation(collation);
		column.getSpecifics().put("firebird.column.collationId", "0");
		FirebirdUtils.setDbType(column, type, 0, length, 0, 0, 0);
		column.setLength(length);
		FirebirdUtils.setBinaryIfOctets(column);
		return column;
	}

	@Test
	void octetsTextBecomesBinary() {
		Column column = read(14, 16, "OCTETS");
		assertEquals(DataType.BINARY, column.getDataType());
		assertEquals(16L, column.getLength());
		assertNull(column.getCharacterSet());
		assertNull(column.getCollation());
		assertNull(column.getSpecifics().get("firebird.column.collationId"));
		column = read(37, 40, null);
		assertEquals(DataType.VARBINARY, column.getDataType());
		assertEquals(40L, column.getLength());
	}

	@Test
	void otherCharacterSetsStayText() {
		final Column column = new Column("V").setCharacterSet("UTF8");
		FirebirdUtils.setDbType(column, 14, 0, 64, 0, 0, 0);
		FirebirdUtils.setBinaryIfOctets(column);
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals("UTF8", column.getCharacterSet());
	}

	@Test
	void binaryIsRecreatedForEachVersion() {
		final Column binary = read(14, 16, "OCTETS");
		final Column varbinary = read(37, 40, null);
		for (final var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect25,
				DialectHolder.defaultDialect30 }) {
			assertEquals("CHAR(16) CHARACTER SET OCTETS",
					dialect.createSqlBuilder().addTypeDefinition(binary).toString());
			assertEquals("VARCHAR(40) CHARACTER SET OCTETS",
					dialect.createSqlBuilder().addTypeDefinition(varbinary).toString());
		}
		assertEquals("BINARY(16)",
				DialectHolder.defaultDialect40.createSqlBuilder().addTypeDefinition(binary).toString());
		assertEquals("VARBINARY(40)",
				DialectHolder.defaultDialect50.createSqlBuilder().addTypeDefinition(varbinary).toString());
	}
}
