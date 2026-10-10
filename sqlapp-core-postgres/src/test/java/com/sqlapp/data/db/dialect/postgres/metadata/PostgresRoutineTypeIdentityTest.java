package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.NamedArgument;

class PostgresRoutineTypeIdentityTest {
	@Test
	void includesArrayDimensionsAndQualifiedNamesInRoutineIdentity() {
		assertEquals("INT", PostgresUtils.typeName(new NamedArgument().setDataType(DataType.INT)));
		assertEquals("UUID[][]",
				PostgresUtils.typeName(new NamedArgument().setDataType(DataType.UUID).setArrayDimension(2)));
		assertEquals("\"Other.Schema\".\"State.Type\"[]", PostgresUtils
				.typeName(new NamedArgument().setDataTypeName("\"Other.Schema\".\"State.Type\"").setArrayDimension(1)));
	}

	@Test
	void rejectsOutOfRangeOidBeforeExecutingJdbc() {
		for (String oid : new String[] { "-1", "4294967296" }) {
			var error = assertThrows(IllegalArgumentException.class,
					() -> PostgresUtils.getTypeInfoById(null, DialectHolder.postgreSQL150, oid));
			assertTrue(error.getMessage().contains(oid));
		}
	}
}
