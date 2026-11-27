/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * Oracle Dialect自体の設定値と、それによって生成されるSQLの検証
 */
class OracleDialectCorrectnessTest {

	private static final List<Dialect> ALL_DIALECTS = List.of(DialectHolder.defaultDialect,
			DialectHolder.oracle10gDialect, DialectHolder.oracle11gDialect, DialectHolder.oracle11gR2Dialect,
			DialectHolder.oracle12cDialect, DialectHolder.oracle18cDialect, DialectHolder.oracle19cDialect,
			DialectHolder.oracle21cDialect, DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect);

	private static final List<Dialect> VIRTUAL_COLUMN_DIALECTS = List.of(DialectHolder.oracle11gDialect,
			DialectHolder.oracle11gR2Dialect, DialectHolder.oracle12cDialect, DialectHolder.oracle19cDialect,
			DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect);

	@Test
	void dateDefaultLiteralsUseOracleFunctions() {
		for (Dialect dialect : ALL_DIALECTS) {
			assertEquals("CURRENT_DATE", dialect.getDbDataTypes().getDbType(DataType.DATETIME).getDefaultValueLiteral(),
					dialect.toString());
			assertEquals("TRUNC(CURRENT_DATE)", dialect.getCurrentDateFunction(), dialect.toString());
			assertEquals("SYS_GUID()", dialect.getDbDataTypes().getDbType(DataType.UUID).getDefaultValueLiteral(),
					dialect.toString());
		}
	}

	@Test
	void createdAtDateColumnInsertsCurrentDate() {
		Table table = new Table("T");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("created_at").setDataType(DataType.DATETIME));
		table.setPrimaryKey(table.getColumns().get("ID"));
		var row = table.newRow();
		row.put("ID", 1);
		table.getRows().add(row);
		String sql = sql(DialectHolder.oracle19cDialect, table, SqlType.INSERT);
		assertFalse(sql.contains("CURRENT_DATETIME"), sql);
		assertTrue(sql.contains("CURRENT_DATE"), sql);
	}

	@Test
	void virtualColumnsAreSupportedFrom11g() {
		assertFalse(DialectHolder.defaultDialect.supportsColumnFormula());
		assertFalse(DialectHolder.oracle10gDialect.supportsColumnFormula());
		for (Dialect dialect : VIRTUAL_COLUMN_DIALECTS) {
			assertTrue(dialect.supportsColumnFormula(), dialect.toString());
		}
		assertInstanceOf(Oracle11g.class, DialectHolder.oracle11gR2Dialect);
	}

	@Test
	void virtualColumnsAreExcludedFromDml() {
		for (Dialect dialect : VIRTUAL_COLUMN_DIALECTS) {
			for (SqlType sqlType : List.of(SqlType.INSERT, SqlType.UPDATE, SqlType.MERGE,
					SqlType.INSERT_SELECT_NOT_EXISTS)) {
				String sql = sql(dialect, virtualColumnTable(), sqlType);
				assertFalse(sql.contains("DOUBLE_ID"), dialect + " " + sqlType + "\n" + sql);
				assertFalse(sql.contains("ID*2"), dialect + " " + sqlType + "\n" + sql);
			}
		}
	}

	@Test
	void virtualColumnIsCreatedAsVirtual() {
		for (Dialect dialect : VIRTUAL_COLUMN_DIALECTS) {
			String sql = sql(dialect, virtualColumnTable(), SqlType.CREATE);
			assertTrue(sql.contains("GENERATED ALWAYS AS (ID*2) VIRTUAL"), dialect + "\n" + sql);
		}
	}

	@Test
	void insertSelectNotExistsOmitsFromOnlyWhenFromLessSelectIsSupported() {
		String sql19c = sql(DialectHolder.oracle19cDialect, simpleTable(), SqlType.INSERT_SELECT_NOT_EXISTS);
		assertTrue(sql19c.contains("FROM DUAL"), sql19c);
		for (Dialect dialect : List.of(DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			String sql = sql(dialect, simpleTable(), SqlType.INSERT_SELECT_NOT_EXISTS);
			assertFalse(sql.matches("(?s).*FROM\\s+WHERE.*"), sql);
			assertFalse(sql.contains("FROM DUAL"), sql);
			assertTrue(sql.contains("WHERE NOT EXISTS"), sql);
		}
	}

	@Test
	void mergeDoesNotUseAsForTableAliases() {
		for (Dialect dialect : List.of(DialectHolder.oracle11gDialect, DialectHolder.oracle19cDialect,
				DialectHolder.oracle23aiDialect)) {
			String sql = sql(dialect, simpleTable(), SqlType.MERGE);
			assertFalse(sql.contains("AS _target_"), sql);
			assertFalse(sql.contains("AS _source_"), sql);
			assertTrue(sql.contains("_target_"), sql);
		}
	}

	@Test
	void characterSemanticsAreKeptForExtendedVarchar2() {
		for (Dialect dialect : List.of(DialectHolder.oracle11gR2Dialect, DialectHolder.oracle12cDialect,
				DialectHolder.oracle19cDialect, DialectHolder.oracle23aiDialect)) {
			assertEquals(2, dialect.getDbDataTypes().getDbType(DataType.VARCHAR).getSupportCharacterSemantics().size(),
					dialect.toString());
			Table table = new Table("T");
			table.getColumns().add(new Column("SHORT_TEXT").setDataType(DataType.VARCHAR).setLength(100)
					.setCharacterSemantics(CharacterSemantics.Char));
			String sql = sql(dialect, table, SqlType.CREATE).toUpperCase(Locale.ROOT);
			assertTrue(sql.contains("VARCHAR2(100 CHAR)"), dialect + "\n" + sql);
		}
		Table table = new Table("T");
		table.getColumns().add(new Column("LONG_TEXT").setDataType(DataType.VARCHAR).setLength(5000)
				.setCharacterSemantics(CharacterSemantics.Char));
		String sql = sql(DialectHolder.oracle19cExtendedDialect, table, SqlType.CREATE).toUpperCase(Locale.ROOT);
		assertTrue(sql.contains("VARCHAR2(5000 CHAR)"), sql);
		assertEquals(2, DialectHolder.oracle19cExtendedDialect.getDbDataTypes().getDbType(DataType.VARCHAR, 5000L)
				.getSupportCharacterSemantics().size());
	}

	@Test
	void matchDataTypeNameDoesNotMatchEverything() {
		Dialect dialect = DialectHolder.oracle19cDialect;
		assertTrue(dialect.matchDataTypeName(DataType.VARCHAR, "VARCHAR2"));
		assertTrue(dialect.matchDataTypeName(DataType.NVARCHAR, "nvarchar2"));
		assertFalse(dialect.matchDataTypeName(DataType.VARCHAR, "NUMBER"));
		assertFalse(dialect.matchDataTypeName(DataType.INT, "VARCHAR2"));
		assertFalse(dialect.matchDataTypeName(null, "VARCHAR2"));
	}

	@Test
	void systemSchemasCoverModernOracleMaintainedUsersAndAreNotShared() {
		Dialect dialect = DialectHolder.oracle19cDialect;
		List<String> schemas = Arrays.asList(dialect.getSystemSchema());
		for (String schema : List.of("SYS", "SYSTEM", "XDB", "AUDSYS", "GSMADMIN_INTERNAL", "WMSYS", "LBACSYS")) {
			assertTrue(schemas.contains(schema), schema);
		}
		assertNotSame(dialect.getSystemSchema(), dialect.getSystemSchema());
	}

	@Test
	void temporaryTableNameHonoursSchemaDecoration() {
		Dialect dialect = DialectHolder.oracle19cDialect;
		Table table = new Table("WORK");
		table.setSchemaName("APP");
		assertEquals("APP.TMP_WORK", dialect.getTemporaryTableName(table, "TMP_", null, true));
		assertEquals("TMP_WORK", dialect.getTemporaryTableName(table, "TMP_", null, false));
	}

	@Test
	void dialectEqualityIsPerVersion() {
		assertEquals(DialectHolder.oracle19cDialect, new OracleDialectResolverAccess().resolve(19, 0));
		assertFalse(DialectHolder.oracle19cDialect.equals(DialectHolder.oracle21cDialect));
		assertFalse(DialectHolder.oracle11gDialect.equals(DialectHolder.oracle11gR2Dialect));
	}

	private static Table simpleTable() {
		Table table = new Table("T");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey(table.getColumns().get("ID"));
		var row = table.newRow();
		row.put("ID", 1);
		row.put("NAME", "a");
		table.getRows().add(row);
		return table;
	}

	private static Table virtualColumnTable() {
		Table table = simpleTable();
		table.getColumns().add(new Column("DOUBLE_ID").setDataType(DataType.INT).setFormula("ID*2"));
		return table;
	}

	private static String sql(final Dialect dialect, final Table table, final SqlType sqlType) {
		table.setDialect(dialect);
		var registry = dialect.createSqlFactoryRegistry();
		final com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(table, sqlType);
		return CommonUtils.first(factory.createSql(table)).getSqlText();
	}

	private static final class OracleDialectResolverAccess {
		Dialect resolve(final int major, final int minor) {
			return new com.sqlapp.data.db.dialect.oracle.resolver.OracleDialectResolver().getDialect(major, minor);
		}
	}
}
