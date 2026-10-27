/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdObjectRecreationSqlTest {
	@Test
	void domainReferencesRenderNamesWithoutRepeatingTypesOrArrayBounds() {
		Table table = new Table("domain_table");
		table.getColumns()
				.add(new Column("value").setDomainName("named domain").setDataType(DataType.INT).setDefaultValue("9"));
		table.getColumns().add(new Column("array").setDomainName("array domain").setArrayDimension(2));
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"named domain\" DEFAULT 9"), sql);
		assertTrue(sql.contains("\"array domain\""), sql);
		table.getColumns().get(0).setDataType(DataType.VARCHAR).setCharacterSet("UTF8");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void externalTablesPreserveQuotedFileNamesAndRejectMissingOrInvalidOptions() {
		Table table = new Table("external").setTableType(Table.TableType.File);
		table.getColumns().add(new Column("value").setDataType(DataType.CHAR).setLength(8));
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getSpecifics().put("firebird.external.file", "/data/it's.dat");
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText()
				.contains("EXTERNAL FILE '/data/it''s.dat'"));
		table.getColumns().get(0).setDataType(DataType.BLOB);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void recreatesCharacterDomainsWithTheirConstraintsAndComments() {
		Domain domain = new Domain("text domain").setDataType(DataType.VARCHAR).setLength(12).setCharacterSet("UTF8")
				.setCollation("UNICODE_CI").setDefaultValue("'initial'").setNotNull(true)
				.setCheck("CHAR_LENGTH(VALUE)>0").setRemarks("it's 日本語");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE);
		assertEquals(2, sql.size());
		assertEquals(
				"CREATE DOMAIN \"text domain\" AS VARCHAR(12) CHARACTER SET UTF8 DEFAULT 'initial' NOT NULL CHECK (CHAR_LENGTH(VALUE)>0) COLLATE UNICODE_CI",
				sql.get(0).getSqlText());
		assertEquals("COMMENT ON DOMAIN \"text domain\" IS 'it''s 日本語'", sql.get(1).getSqlText());
		domain.setOnUpdate("CURRENT_TIMESTAMP");
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE));
	}

	@Test
	void recreatesMultidimensionalDomainBoundsAndRejectsIncompleteBounds() {
		Domain domain = new Domain("array_domain").setDataType(DataType.INT).setArrayDimension(2);
		domain.getSpecifics().put("firebird.array.bounds", "-2:2,3:4");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		assertTrue(registry.createSql(domain, SqlType.CREATE).get(0).getSqlText().contains("INTEGER[-2:2,3:4]"));
		domain.getSpecifics().remove("firebird.array.bounds");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(domain, SqlType.CREATE));
	}

	@Test
	void keepsExplicitViewAliasesAndRejectsAnEmptyView() {
		View view = new View("aliased view");
		view.getColumns().add(new Column("renamed value"));
		view.getColumns().get(0).setRemarks("view column");
		view.setStatement("SELECT 1 FROM RDB$DATABASE");
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		String ddl = registry.createSql(view, SqlType.CREATE).get(0).getSqlText();
		assertTrue(ddl.matches("(?s).*\\(\\s*\"renamed value\"\\) AS\\s*SELECT 1.*"), ddl);
		assertEquals("COMMENT ON COLUMN \"aliased view\".\"renamed value\" IS 'view column'",
				registry.createSql(view, SqlType.CREATE).get(1).getSqlText());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new View("empty"), SqlType.CREATE));
	}

	@Test
	void commentsQuoteIdentifiersAndSupportRemovalAndVersionBoundaries() {
		var registry = DialectHolder.defaultDialect25.createSqlFactoryRegistry();
		Column column = new Column("a\"b").setTableName("table name");
		assertEquals("COMMENT ON COLUMN \"table name\".\"a\"\"b\" IS NULL",
				registry.createSql(column, SqlType.SET_COMMENT).get(0).getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new Column("unbound"), SqlType.SET_COMMENT));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(new Table("old"), SqlType.SET_COMMENT));
		assertEquals("COMMENT ON EXTERNAL FUNCTION \"new\" IS NULL",
				registry.createSql(new Function("new"), SqlType.SET_COMMENT).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new com.sqlapp.data.schemas.Package("new"), SqlType.SET_COMMENT));
	}

	@Test
	void structuralRoutineAlterRestoresItsOwnCommentExactlyOnce() {
		Procedure original = new Procedure("routine").setRemarks("before").setStatement("BEGIN END");
		Procedure target = original.clone().setRemarks("after").setStatement("BEGIN POST_EVENT 'changed'; END");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().startsWith("ALTER PROCEDURE"));
		assertEquals("COMMENT ON PROCEDURE \"routine\" IS 'after'", sql.get(1).getSqlText());
	}
}
