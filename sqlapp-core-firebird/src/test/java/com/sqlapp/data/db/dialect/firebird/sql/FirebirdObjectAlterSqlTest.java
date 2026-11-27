/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdObjectAlterSqlTest {
	@Test
	void altersDomainTypeDefaultAndCheckWithoutDroppingTheDomain() {
		Domain original = new Domain("domain name").setDataType(DataType.INT).setDefaultValue("3").setCheck("VALUE>0");
		Domain target = original.clone().setDataType(DataType.BIGINT).setDefaultValue("7").setCheck("VALUE>=5");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(4, sql.size());
		assertEquals("ALTER DOMAIN \"domain name\" TYPE BIGINT", sql.get(0).getSqlText());
		assertEquals("ALTER DOMAIN \"domain name\" SET DEFAULT 7", sql.get(1).getSqlText());
		assertEquals("ALTER DOMAIN \"domain name\" DROP CONSTRAINT", sql.get(2).getSqlText());
		assertEquals("ALTER DOMAIN \"domain name\" ADD CHECK (VALUE>=5)", sql.get(3).getSqlText());
	}

	@Test
	void domainNullabilityHasA30BoundaryAndUnsupportedChangesFail() {
		Domain original = new Domain("d").setDataType(DataType.INT);
		Domain target = original.clone().setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
		assertTrue(DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().endsWith(" SET NOT NULL"));
		target.setName("renamed");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect40
				.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
		Domain text = new Domain("text").setDataType(DataType.VARCHAR).setLength(12).setCharacterSet("UTF8")
				.setCollation("UNICODE_CI");
		Domain longer = text.clone().setLength(20);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(text.diff(longer), SqlType.ALTER));
		longer.setCollation("UNICODE");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect25
				.createSqlFactoryRegistry().createSql(text.diff(longer), SqlType.ALTER));
		Domain defaultText = new Domain("default_text").setDataType(DataType.VARCHAR).setLength(12)
				.setCharacterSet("UTF8").setCollation("UTF8");
		defaultText.getSpecifics().put("firebird.domain.collationId", "0");
		assertEquals("ALTER DOMAIN \"default_text\" TYPE VARCHAR(20)",
				DialectHolder.defaultDialect25.createSqlFactoryRegistry()
						.createSql(defaultText.diff(defaultText.clone().setLength(20)), SqlType.ALTER).get(0)
						.getSqlText());
	}

	@Test
	void altersViewQueryAndColumnCommentsWithoutDroppingGrants() {
		View original = new View("v");
		original.getColumns().add(new Column("n").setRemarks("before"));
		original.setStatement("SELECT 1 FROM RDB$DATABASE");
		View target = (View) original.clone();
		target.setStatement("SELECT 2 FROM RDB$DATABASE");
		target.getColumns().get(0).setRemarks(null);
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("ALTER VIEW"));
		assertEquals("COMMENT ON COLUMN \"v\".\"n\" IS NULL", sql.get(1).getSqlText());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect20.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER));
		target.getColumns().get(0).setName("renamed n");
		assertTrue(DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().contains("\"renamed n\""));
		original.setStatement("SELECT 1 FROM RDB$DATABASE WITH CHECK OPTION");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect25
				.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
		assertTrue(DialectHolder.defaultDialect30.createSqlFactoryRegistry()
				.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().startsWith("ALTER VIEW"));
	}

	@Test
	void commentOnlyViewChangesNeedNoQueryAndRejectUnrenderedColumnProperties() {
		View original = new View("v");
		original.getColumns().add(new Column("n"));
		View target = (View) original.clone();
		target.getColumns().get(0).setRemarks("日本語");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		assertTrue(
				registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().startsWith("COMMENT ON"));
		target.getColumns().get(0).setNotNull(true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void triggerAlterResetsPositionToZeroAndKeepsItsComment() {
		Trigger original = new Trigger("t").setTableName("table").setActionTiming("BEFORE")
				.setEventManipulation("INSERT").setStatement("BEGIN END").setRemarks("remark");
		original.getSpecifics().put("POSITION", "7");
		Trigger target = original.clone().setEnable(false);
		target.getSpecifics().put("POSITION", "0");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("CREATE OR ALTER TRIGGER"));
		assertTrue(sql.get(0).getSqlText().contains("POSITION 0"));
		assertTrue(sql.get(0).getSqlText().contains("INACTIVE"));
		target.setTableName("other");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect25
				.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void triggerSecurityCanBeClearedExplicitly() {
		Trigger original = new Trigger("t").setTableName("table").setActionTiming("BEFORE")
				.setEventManipulation("INSERT").setStatement("BEGIN END");
		original.getSpecifics().put("firebird.sqlSecurity", "DEFINER");
		Trigger target = original.clone();
		target.setSpecifics(new DbInfo());
		assertTrue(original.getSpecifics().containsKey("firebird.sqlSecurity"));
		var difference = original.diff(target);
		assertTrue(((Trigger) difference.getOriginal()).getSpecifics().containsKey("firebird.sqlSecurity"));
		assertFalse(((Trigger) difference.getTarget()).getSpecifics().containsKey("firebird.sqlSecurity"));
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		assertEquals(FirebirdAlterTriggerFactory.class, registry.getSqlFactory(difference, SqlType.ALTER).getClass());
		var sql = registry.createSql(difference, SqlType.ALTER);
		assertEquals(2, sql.size(), sql.toString());
		assertEquals("ALTER TRIGGER \"t\" DROP SQL SECURITY", sql.get(1).getSqlText());
	}

	@Test
	void indexesAlterOnlyStateAndComments() {
		Index original = new Index("i");
		Index target = original.clone().setEnable(false).setRemarks("inactive");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertEquals("ALTER INDEX \"i\" INACTIVE",
				registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText());
		target.setUnique(true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		Table table = new Table("table");
		table.getConstraints().add(new UniqueConstraint("pk", true));
		table.getIndexes().add(new Index("pk").setEnable(false));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(table.getIndexes().get("pk"), SqlType.ALTER));
	}
}
