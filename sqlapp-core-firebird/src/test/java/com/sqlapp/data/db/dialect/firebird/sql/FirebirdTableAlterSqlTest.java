/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdTableAlterSqlTest {
	private Table table() {
		Table table = new Table("table name");
		table.getColumns().add(new Column("value").setDataType(DataType.INT).setDefaultValue("3"));
		return table;
	}

	@Test
	void altersTypesDefaultsAndCommentsAsSeparateStatements() {
		Table original = table();
		Table target = original.clone();
		target.getColumns().get(0).setDataType(DataType.BIGINT).setDefaultValue("7").setRemarks("日本語");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(3, sql.size());
		assertEquals("ALTER TABLE \"table name\" ALTER COLUMN \"value\" TYPE BIGINT", sql.get(0).getSqlText());
		assertEquals("ALTER TABLE \"table name\" ALTER COLUMN \"value\" SET DEFAULT 7", sql.get(1).getSqlText());
		assertEquals("COMMENT ON COLUMN \"table name\".\"value\" IS '日本語'", sql.get(2).getSqlText());
	}

	@Test
	void columnRenameUsesAlterToAndDomainChangesUseTheDomainName() {
		Table original = table();
		Table target = original.clone();
		target.getColumns().get(0).setName("renamed").setDomainName("named domain");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size(), sql.stream().map(s -> s.getSqlText()).toList().toString());
		assertEquals("ALTER TABLE \"table name\" ALTER COLUMN \"value\" TO \"renamed\"", sql.get(0).getSqlText());
		assertEquals("ALTER TABLE \"table name\" ALTER COLUMN \"renamed\" TYPE \"named domain\"",
				sql.get(1).getSqlText());
	}

	@Test
	void addsAndDropsColumnsWithFirebirdSyntax() {
		Table original = table();
		Table target = original.clone();
		target.getColumns().add(new Column("added").setDataType(DataType.INT).setRemarks("added"));
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		var sql = registry.createSql(original.diff(target), SqlType.ALTER);
		assertTrue(sql.get(0).getSqlText().startsWith("ALTER TABLE \"table name\" ADD \"added\" INTEGER"));
		assertEquals(2, sql.size(), sql.stream().map(s -> s.getSqlText()).toList().toString());
		assertEquals("ALTER TABLE \"table name\" DROP \"added\"",
				registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText());
	}

	@Test
	void guardsNullabilityVersionsAndUnrenderedChanges() {
		Table original = table();
		Table target = original.clone();
		target.getColumns().get(0).setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
		assertTrue(DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().endsWith(" SET NOT NULL"));
		target.setName("renamed table");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect40
				.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void tableCommentsAndSecurityDoNotRecreateTheTable() {
		Table original = table();
		Table target = original.clone().setRemarks("remark");
		target.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		var sql = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(2, sql.size(), sql.stream().map(s -> s.getSqlText()).toList().toString());
		assertTrue(sql.get(0).getSqlText().startsWith("COMMENT ON TABLE"));
		assertTrue(sql.get(1).getSqlText().contains("ALTER TABLE \"table name\" ALTER"));
		assertTrue(sql.get(1).getSqlText().contains("SQL SECURITY INVOKER"));
		assertEquals("ALTER TABLE \"table name\" DROP SQL SECURITY",
				registry.createSql(target.diff(original), SqlType.ALTER).get(1).getSqlText());
	}

	@Test
	void typeChangesRequireKnownDefaultCollation() {
		Table original = table();
		Column column = original.getColumns().get(0);
		column.setDataType(DataType.VARCHAR).setLength(12).setCharacterSet("UTF8").setCollation("UNICODE_CI");
		column.getSpecifics().put("firebird.column.collationId", "2");
		Table target = original.clone();
		target.getColumns().get(0).setLength(20);
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		column.getSpecifics().put("firebird.column.collationId", "0");
		column.setCollation("UTF8");
		Table safe = original.clone();
		safe.getColumns().get(0).setLength(20);
		assertEquals(1, registry.createSql(original.diff(safe), SqlType.ALTER).size());
	}

	@Test
	void tableOptionsAreRejectedAndComputedTypeChangesKeepTheExpression() {
		Table original = table();
		Table target = original.clone();
		target.getSpecifics().put("firebird.external.file", "/tmp/file");
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		original.getColumns().get(0).setDefaultValue(null).setFormula("1+2");
		Table computed = original.clone();
		computed.getColumns().get(0).setDataType(DataType.BIGINT);
		assertEquals("ALTER TABLE \"table name\" ALTER COLUMN \"value\" TYPE BIGINT COMPUTED BY (1+2)",
				registry.createSql(original.diff(computed), SqlType.ALTER).get(0).getSqlText());
	}

	@Test
	void unmatchedRenameAndTypeChangesCannotSilentlyDropData() {
		Table original = table();
		original.getColumns().add(new Column("text").setDataType(DataType.VARCHAR).setLength(12));
		Table target = original.clone();
		target.getColumns().get(0).setName("renamed").setDataType(DataType.BIGINT);
		target.getColumns().get(1).setLength(20);
		target.getColumns().add(new Column("added").setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect40.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
	}
}
