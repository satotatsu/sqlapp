/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class Db2I75BinaryTypeTest {
	private final Db2I75 dialect = new Db2I75();

	@ParameterizedTest(name = "{0}({1}) -> {2}")
	@CsvSource(value = {
			"BINARY, NULL, BINARY(1)",
			"BINARY, 16, BINARY(16)",
			"VARBINARY, NULL, VARBINARY(32740)",
			"VARBINARY, 1000, VARBINARY(1000)",
			"LONGVARBINARY, NULL, VARBINARY(32740)",
			"LONGVARBINARY, 32740, VARBINARY(32740)",
			"LONGVARBINARY, 40000, BLOB" }, nullValues = "NULL")
	void binaryStringsUseNativeTypes(DataType type, Long length, String expectedSql) {
		var column = new Column("C").setDataType(type);
		if (length != null)
			column.setLength(length);
		assertEquals(expectedSql, dialect.createSqlBuilder().addTypeDefinition(column).toString());
	}

	@Test
	void deprecatedLongVarbinaryResolvesToNativeVarbinary() {
		var types = dialect.getDbDataTypes();
		assertSame(types.getDbTypeStrict(DataType.VARBINARY), types.getDbType(DataType.LONGVARBINARY));
		assertSame(types.getDbTypeStrict(DataType.VARBINARY), types.getDbType(DataType.LONGVARBINARY, 1000L));
	}

	@Test
	void forBitDataColumnsReadFromTheCatalogKeepTheirSpelling() {
		var column = new Column("C").setDataType(DataType.VARCHAR).setLength(100L);
		column.getSpecifics().put("CODEPAGE", "0");
		assertEquals("VARCHAR(100) FOR BIT DATA", dialect.createSqlBuilder().addTypeDefinition(column).toString());
	}

	@Test
	void forBitDataTypeNamesResolveToNativeBinaryTypes() {
		var types = dialect.getDbDataTypes();
		assertEquals(DataType.VARBINARY,
				types.matchTypeInformation("VARCHAR(10) FOR BIT DATA", null).orElseThrow().getDataType().orElseThrow());
		assertEquals(DataType.BINARY,
				types.matchTypeInformation("CHAR(10) FOR BIT DATA", null).orElseThrow().getDataType().orElseThrow());
	}
}
