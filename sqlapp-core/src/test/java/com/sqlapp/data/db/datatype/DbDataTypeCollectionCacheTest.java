package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DbDataTypeCollectionCacheTest {
	@Test
	void registrationAfterResolutionReplacesCachedType() {
		DbDataTypeCollection types = new DbDataTypeCollection();
		types.addVarBinary("OLD VARBINARY", 100, type -> {
		});
		assertEquals("OLD VARBINARY", types.getDbType(DataType.VARBINARY).getTypeName());

		types.addVarBinary("VARBINARY", 100, type -> {
		});

		assertEquals("VARBINARY", types.getDbType(DataType.VARBINARY).getTypeName());
		assertSame(types.getDbTypeStrict(DataType.VARBINARY), types.getDbType(DataType.VARBINARY));
		assertSame(types.getDbType(DataType.VARBINARY, 50L), types.getDbType(DataType.VARBINARY));
	}

	@Test
	void registrationAfterFailedProductMatchMakesTheNameResolvable() {
		DbDataTypeCollection types = new DbDataTypeCollection();
		assertTrue(types.matchTypeInformation("INT", null).isEmpty());

		types.addInt();

		assertEquals(DataType.INT, types.matchTypeInformation("INT", null).orElseThrow().getDataType().orElseThrow());
	}

	@Test
	void deprecationAfterResolutionReturnsTheSurrogate() {
		DbDataTypeCollection types = new DbDataTypeCollection();
		types.addBlob("BLOB", 1000, type -> {
		});
		types.addVarBinary("VARBINARY", 100, type -> {
		});
		DbDataType<?> blob = types.getDbType(DataType.BLOB);
		assertEquals("BLOB", blob.getTypeName());

		blob.setDeprecated(types.getDbTypeStrict(DataType.VARBINARY));

		assertSame(types.getDbTypeStrict(DataType.VARBINARY), types.getDbType(DataType.BLOB));
	}
}
