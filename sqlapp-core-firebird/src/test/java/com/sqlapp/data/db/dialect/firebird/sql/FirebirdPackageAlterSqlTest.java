/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.DbInfo;

class FirebirdPackageAlterSqlTest {
	private Package header() {
		return new Package("quoted package").setStatement("BEGIN FUNCTION V() RETURNS INTEGER; END");
	}

	@Test
	void headerAlterUsesExistingRendererAndQuotesNames() {
		Package original = header();
		Package target = original.clone().setStatement("BEGIN FUNCTION V() RETURNS BIGINT; END").setRemarks("日本語");
		var sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("ALTER PACKAGE \"quoted package\""));
		assertTrue(sql.get(0).getSqlText().contains("RETURNS BIGINT"));
		assertTrue(sql.get(1).getSqlText().startsWith("COMMENT ON PACKAGE"));
	}

	@Test
	void bodyChangesUseRecreateWithoutDroppingTheHeader() {
		PackageBody original = new PackageBody("quoted package")
				.setStatement("BEGIN FUNCTION V() RETURNS INTEGER AS BEGIN RETURN 1; END END");
		var sql = DialectHolder.defaultDialect30.createSqlFactoryRegistry().createSql(
				original.diff(
						original.clone().setStatement("BEGIN FUNCTION V() RETURNS INTEGER AS BEGIN RETURN 2; END END")),
				SqlType.ALTER);
		assertEquals(1, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("RECREATE PACKAGE BODY \"quoted package\""));
		assertFalse(sql.get(0).getSqlText().contains("DROP PACKAGE"));
	}

	@Test
	void commentOnlyChangesDoNotRecompileAndCanClearComments() {
		Package original = header();
		Package target = original.clone().setRemarks("remark");
		var registry = DialectHolder.defaultDialect30.createSqlFactoryRegistry();
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		assertEquals("COMMENT ON PACKAGE \"quoted package\" IS NULL",
				registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText());
	}

	@Test
	void securityCanBeSetAndClearedWithVersionBoundaries() {
		Package original = header();
		Package target = original.clone();
		target.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.contains("SQL SECURITY INVOKER"));
		Package cleared = target.clone().setSpecifics(new DbInfo());
		assertFalse(
				registry.createSql(target.diff(cleared), SqlType.ALTER).get(0).getSqlText().contains("SQL SECURITY"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void shadowedStatementsRenamesAndUnsupportedPropertiesAreRejected() {
		Package original = header();
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(original.clone().setName("renamed")), SqlType.ALTER));
		Package defined = original.clone().setDefinition("CREATE PACKAGE old AS BEGIN END");
		Package shadowed = defined.clone().setStatement("BEGIN END");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(defined.diff(shadowed), SqlType.ALTER));
		Package unsupported = original.clone();
		unsupported.getSpecifics().put("unknown", "value");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(unsupported), SqlType.ALTER));
		PackageBody body = new PackageBody("p").setStatement("BEGIN END");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(body.diff(body.clone().setRemarks("body comment")), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class, () -> registry
				.createSql(new PackageBody("p").setDefinition("ALTER PACKAGE p AS BEGIN END"), SqlType.ALTER));
	}
}
