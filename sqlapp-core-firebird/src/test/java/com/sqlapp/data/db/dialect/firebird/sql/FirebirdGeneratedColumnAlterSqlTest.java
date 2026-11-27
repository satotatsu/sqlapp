/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdGeneratedColumnAlterSqlTest {
	private Table table(boolean identity) {
		Table table = new Table("quoted table");
		table.getColumns().add(new Column("generated value").setDataType(DataType.INT).setIdentity(identity));
		if (identity)
			table.getColumns().get(0).setIdentityGenerationType(IdentityGenerationType.ByDefault).setIdentityStep(1);
		else
			table.getColumns().get(0).setFormula("1+2");
		return table;
	}

	@Test
	void computedExpressionAndTypeAreRenderedTogetherAcrossVersions() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50)) {
			Table original = table(false), target = original.clone();
			target.getColumns().get(0).setDataType(DataType.BIGINT).setFormula("2*3");
			var sql = dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER);
			assertEquals(1, sql.size());
			assertEquals("ALTER TABLE \"quoted table\" ALTER COLUMN \"generated value\" TYPE BIGINT COMPUTED BY (2*3)",
					sql.get(0).getSqlText());
		}
	}

	@Test
	void expressionRenameAndCommentPreserveTheNewColumnName() {
		Table original = table(false), target = original.clone();
		target.getColumns().get(0).setName("renamed value").setFormula("5+6").setRemarks("日本語");
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(3, sql.size());
		assertTrue(sql.get(0).getSqlText().endsWith(" TO \"renamed value\""));
		assertTrue(sql.get(1).getSqlText().endsWith("\"renamed value\" COMPUTED BY (5+6)"));
		assertTrue(sql.get(2).getSqlText().contains(".\"renamed value\" IS '日本語'"));
	}

	@Test
	void rejectsConversionsAndInvalidComputedOptions() {
		Table original = table(false), regular = original.clone();
		regular.getColumns().get(0).setFormula(null);
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(regular), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(regular.diff(original), SqlType.ALTER));
		Table invalid = original.clone();
		invalid.getColumns().get(0).setDefaultValue("3");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(invalid), SqlType.ALTER));
		Table blank = original.clone();
		blank.getColumns().get(0).setFormula(" ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(blank), SqlType.ALTER));
	}

	@Test
	void altersIdentityGenerationAndIncrementWithoutRestart() {
		Table original = table(true), target = original.clone();
		target.getColumns().get(0).setIdentityGenerationType(IdentityGenerationType.Always).setIdentityStep(-3);
		var sql = DialectHolder.defaultDialect40.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().endsWith(" SET GENERATED ALWAYS"));
		assertTrue(sql.get(1).getSqlText().endsWith(" SET INCREMENT BY -3"));
		assertEquals(1L, original.getColumns().get(0).getIdentityStep());
		assertEquals(IdentityGenerationType.ByDefault, original.getColumns().get(0).getIdentityGenerationType());
	}

	@Test
	void dropsIdentityWithoutDroppingTheColumn() {
		Table original = table(true), target = original.clone();
		target.getColumns().get(0).setIdentity(false);
		var sql = DialectHolder.defaultDialect50.createSqlFactoryRegistry().createSql(original.diff(target),
				SqlType.ALTER);
		assertEquals(1, sql.size());
		assertEquals("ALTER TABLE \"quoted table\" ALTER COLUMN \"generated value\" DROP IDENTITY",
				sql.get(0).getSqlText());
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect50
				.createSqlFactoryRegistry().createSql(target.diff(original), SqlType.ALTER));
	}

	@Test
	void rejectsIdentityAlterBefore40AndZeroOrMissingIncrement() {
		Table original = table(true), target = original.clone();
		target.getColumns().get(0).setIdentityStep(2);
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect25, DialectHolder.defaultDialect30))
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER));
		var registry = DialectHolder.defaultDialect40.createSqlFactoryRegistry();
		target.getColumns().get(0).setIdentityStep(0);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getColumns().get(0).setIdentityStep((Number) null);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void configuredStartAndCurrentValueChangesAreNotImplicitRestarts() {
		Table original = table(true), target = original.clone();
		target.getColumns().get(0).setIdentityStartValue(123);
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		Table current = original.clone();
		current.getColumns().get(0).setIdentityLastValue(123);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(current), SqlType.ALTER));
	}
}
