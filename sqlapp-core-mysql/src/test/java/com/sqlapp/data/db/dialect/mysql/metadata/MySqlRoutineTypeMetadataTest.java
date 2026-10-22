/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ParameterDirection;

class MySqlRoutineTypeMetadataTest {

	@Test
	void quotesArgumentNamesAndKeepsDirections() {
		Dialect dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
		for (String name : new String[] { "select", "order value", "value`part", "日本語 value" }) {
			for (ParameterDirection direction : new ParameterDirection[] { ParameterDirection.Input,
					ParameterDirection.Output, ParameterDirection.Inout }) {
				NamedArgument argument = new NamedArgument(name);
				argument.setDataType(DataType.INT);
				argument.setDirection(direction);
				String sql = dialect.createSqlBuilder().argument(argument).toString();
				assertTrue(sql.contains(dialect.quote(name)), sql);
				if (direction != ParameterDirection.Input) {
					assertTrue(sql.startsWith(direction.toString() + " "), sql);
				}
			}
		}
	}

	@Test
	void rejectsInvalidValueLists() {
		Dialect dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
		NamedArgument argument = new NamedArgument("value");
		argument.setDataType(DataType.ENUM);
		assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().argument(argument));
		for (String declaration : new String[] { "enum", "enum()", "enum('a',)", "enum('a' 'b')" }) {
			assertThrows(IllegalArgumentException.class,
					() -> MySqlUtils.setRoutineArgumentValues(declaration, argument));
		}
	}

	@ParameterizedTest
	@CsvSource({ "5,5,3", "5,6,4", "5,7,0", "8,0,0", "8,4,0", "9,0,0" })
	void preservesRoutineTypesThroughReadersAndDdl(int major, int minor, int revision) throws Exception {
		Dialect dialect = DialectResolver.getInstance().getDialect("MySql", major, minor, revision);
		ProductVersionInfo version = new ProductVersionInfo().setMajorVersion(major).setMinorVersion(minor)
				.setRevision(revision);
		for (TypeCase type : new TypeCase[] {
				new TypeCase("int", "int unsigned", DataType.UINT, "INT UNSIGNED", null, 10L, null),
				new TypeCase("bigint", "bigint unsigned", DataType.UBIGINT, "BIGINT UNSIGNED", null, 20L, null),
				new TypeCase("decimal", "decimal(12,3)", DataType.DECIMAL, "DECIMAL(12,3)", null, 12L, 3),
				new TypeCase("enum", "enum('new','done')", DataType.ENUM, "ENUM('new','done')", 4L, null, null),
				new TypeCase("enum", "enum('a, b','O''Reilly','back\\\\slash')", DataType.ENUM,
						"ENUM('a, b','O''Reilly','back\\\\slash')", 12L, null, null),
				new TypeCase("set", "set('red','blue')", DataType.SET, "SET('red','blue')", 8L, null, null),
				new TypeCase("varchar", "varchar(17)", DataType.VARCHAR, "VARCHAR(17)", 17L, null, null) }) {
			assertTypeRoundTrip(dialect, version, type);
		}
		if (major > 5 || minor >= 6) {
			assertTypeRoundTrip(dialect, version,
					new TypeCase("datetime", "datetime(3)", DataType.TIMESTAMP, "DATETIME(3)", null, null, null));
			assertTypeRoundTrip(dialect, version,
					new TypeCase("time", "time(6)", DataType.TIME, "TIME(6)", null, null, null));
		}
		assertTypeRoundTrip(dialect, version,
				new TypeCase("varchar", null, DataType.VARCHAR, "VARCHAR(17)", 17L, null, null));
	}

	private void assertTypeRoundTrip(Dialect dialect, ProductVersionInfo version, TypeCase type) throws Exception {
		try (CachedRowSet rows = row(type)) {
			ExResultSet rs = new ExResultSet(rows);
			Function function = new MySqlFunction553Reader(dialect).createFunction(rs, version);
			assertEquals(type.expectedType(), function.getReturning().getDataType(), type.declaration());
			NamedArgument input = new MySqlFunctionArgument553Reader(dialect).createNamedArgument(rs);
			assertEquals(type.expectedType(), input.getDataType(), type.declaration());
			function.getArguments().add(input);
			SqlFactory<Function> factory = dialect.createSqlFactoryRegistry().getSqlFactory(function, SqlType.CREATE);
			String sql = factory.createSql(function).get(0).getSqlText();
			assertContains(sql, "`order value` " + type.expectedSql());
			boolean valueList = type.expectedType() == DataType.ENUM || type.expectedType() == DataType.SET;
			assertContains(sql, "RETURNS " + (valueList ? type.declaration() : type.expectedSql()));
			if (valueList) {
				assertEquals(type.declaration().substring(type.declaration().indexOf('(') + 1,
						type.declaration().lastIndexOf(')')), String.join(",", input.getValues()));
				assertEquals(type.declaration(), function.getReturning().getDefinition().get(0));
				for (String value : input.getValues()) {
					assertTrue(sql.contains(value), sql);
				}
			}
			assertTrue(sql.contains("SQL SECURITY INVOKER"), sql);
			if (type.length() != null) {
				assertTrue(sql.contains("CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"), sql);
				if (type.expectedType().isCharacter()) {
					assertEquals("utf8mb4", input.getCharacterSet());
				}
				assertEquals("utf8mb4_bin", function.getReturning().getCollation());
			}

			NamedArgument output = new MySqlProcedureArgument553Reader(dialect).createNamedArgument(rs);
			assertEquals(type.expectedType(), output.getDataType(), type.declaration());
			Procedure procedure = new Procedure("routine");
			procedure.setDialect(dialect);
			procedure.setStatement("BEGIN SELECT 1; END");
			procedure.getArguments().add(output);
			SqlFactory<Procedure> procedureFactory = dialect.createSqlFactoryRegistry().getSqlFactory(procedure,
					SqlType.CREATE);
			assertContains(procedureFactory.createSql(procedure).get(0).getSqlText(),
					"`order value` " + type.expectedSql());
		}
	}

	private void assertContains(String sql, String fragment) {
		assertTrue(sql.replaceAll("\\s+", "").contains(fragment.replaceAll("\\s+", "")), sql);
	}

	private CachedRowSet row(TypeCase type) throws SQLException {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("ROUTINE_NAME", "routine");
		values.put("ROUTINE_CATALOG", "def");
		values.put("ROUTINE_SCHEMA", "app");
		values.put("ROUTINE_COMMENT", "comment");
		values.put("CREATED", null);
		values.put("LAST_ALTERED", null);
		values.put("IS_DETERMINISTIC", "YES");
		values.put("ROUTINE_DEFINITION", "RETURN NULL");
		values.put("SQL_DATA_ACCESS", "NO SQL");
		values.put("SECURITY_TYPE", "INVOKER");
		values.put("SPECIFIC_NAME", "routine");
		values.put("SPECIFIC_CATALOG", "def");
		values.put("SPECIFIC_SCHEMA", "app");
		values.put("PARAMETER_NAME", "order value");
		values.put("PARAMETER_MODE", "IN");
		values.put("DATA_TYPE", type.baseType());
		values.put("DTD_IDENTIFIER", type.declaration());
		values.put("CHARACTER_MAXIMUM_LENGTH", type.length());
		values.put("CHARACTER_OCTET_LENGTH", type.length());
		values.put("NUMERIC_PRECISION", type.precision());
		values.put("NUMERIC_SCALE", type.scale());
		values.put("CHARACTER_SET_NAME", type.length() == null ? null : "utf8mb4");
		values.put("COLLATION_NAME", type.length() == null ? null : "utf8mb4_bin");
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(values.size());
		int index = 0;
		for (var entry : values.entrySet()) {
			metadata.setColumnName(++index, entry.getKey());
			metadata.setColumnLabel(index, entry.getKey());
			metadata.setColumnType(index, entry.getValue() instanceof Number ? Types.BIGINT : Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		rows.moveToInsertRow();
		index = 0;
		for (Object value : values.values()) {
			rows.updateObject(++index, value);
		}
		rows.insertRow();
		rows.moveToCurrentRow();
		rows.beforeFirst();
		rows.next();
		return rows;
	}

	private record TypeCase(String baseType, String declaration, DataType expectedType, String expectedSql,
			Long length, Long precision, Integer scale) {
	}
}
