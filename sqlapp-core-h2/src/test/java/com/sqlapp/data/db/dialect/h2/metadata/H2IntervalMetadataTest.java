/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class H2IntervalMetadataTest {
	@Test
	void metadataRecreatesBothQualifiersWithIndependentLeadingAndFractionalPrecision() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(Y INTERVAL YEAR(18) TO MONTH, D INTERVAL DAY(9) TO SECOND(3))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("T");
			var table = reader.getAllFull(connection).get(0);
			var year = table.getColumns().get("Y");
			var day = table.getColumns().get("D");
			assertEquals(DataType.INTERVAL_YEAR_TO_MONTH, year.getDataType());
			assertEquals(18L, year.getLength());
			assertEquals(DataType.INTERVAL_DAY_TO_SECOND, day.getDataType());
			assertEquals(9L, day.getLength());
			assertEquals(3, day.getScale());
			var ddl = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
			statement.execute("DROP TABLE T");
			for (var sql : ddl)
				statement.execute(sql.getSqlText());
			var recreated = reader.getAllFull(connection).get(0);
			for (String name : new String[] { "Y", "D" }) {
				var before = table.getColumns().get(name);
				var after = recreated.getColumns().get(name);
				assertEquals(before.getDataType(), after.getDataType());
				assertEquals(before.getLength(), after.getLength());
				assertEquals(before.getScale(), after.getScale());
			}
		}
	}
}
