/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;

class SqliteStrictAnyMetadataTest {
	@ParameterizedTest
	@ValueSource(ints = { 15, 37 })
	void legacyAndBulkColumnPathsDistinguishStrictAnyFromNumericAffinity(int minor) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.t(id INT PRIMARY KEY, payload any) WITHOUT ROWID, STRICT");
			statement.execute("CREATE TABLE main.t(id INT PRIMARY KEY, payload ANY)");
			var reader = new SqliteColumnReader(DialectHolder.defaultDialect);
			var context = new ParametersContext();
			context.put("schemaName", "other");
			context.put("tableName", "t");
			var version = new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor);
			var columns = reader.doGetAll(connection, context, version);
			assertEquals(DataType.ANY_DATA, columns.get(1).getDataType());
			assertNull(columns.get(1).getDataTypeName());
			assertEquals("any",
					columns.get(1).getSpecifics().get("sqlite.declaredType").toLowerCase(java.util.Locale.ROOT));
			context.put("schemaName", "main");
			assertEquals(DataType.NUMERIC, reader.doGetAll(connection, context, version).get(1).getDataType());
		}
	}
}
