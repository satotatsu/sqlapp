/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdIdentityValidationSqlTest {
	private Table table(Column column) {
		Table table = new Table("t");
		table.getColumns().add(column.setIdentity(true));
		return table;
	}

	@Test
	void rejectsNonIntegerTypesExcessPrecisionAndFractionalScale() {
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		for (Column column : new Column[] { new Column("v").setDataType(DataType.VARCHAR).setLength(20),
				new Column("v").setDataType(DataType.DOUBLE),
				new Column("v").setDataType(DataType.DECIMAL).setLength(38).setScale(0),
				new Column("v").setDataType(DataType.NUMERIC).setLength(10).setScale(2),
				new Column("v").setDataType(DataType.BIGINT).setDataTypeName("INT128") })
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table(column), SqlType.CREATE));
	}

	@Test
	void rejectsDefaultsAndArraysInsteadOfIgnoringThem() {
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry
				.createSql(table(new Column("v").setDataType(DataType.INT).setDefaultValue("3")), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class,
				() -> registry
						.createSql(
								table(new Column("v").setDataType(DataType.INT).setArrayDimension(1)
										.setArrayDimensionLowerBound(1).setArrayDimensionUpperBound(2)),
								SqlType.CREATE));
	}

	@Test
	void acceptsAllSupportedScalarIdentityTypes() {
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		for (DataType type : new DataType[] { DataType.SMALLINT, DataType.INT, DataType.BIGINT, DataType.NUMERIC,
				DataType.DECIMAL }) {
			Column column = new Column("v").setDataType(type);
			if (type == DataType.NUMERIC || type == DataType.DECIMAL)
				column.setLength(18).setScale(0);
			assertTrue(registry.createSql(table(column), SqlType.CREATE).get(0).getSqlText().contains("IDENTITY"));
		}
	}
}
