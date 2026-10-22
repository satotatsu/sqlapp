/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaColumnDdlTest {
	private final Dialect[] dialects = { DialectHolder.defaultDialect, DialectHolder.cloudDialect };

	@Test
	void preservesIdentityGenerationAndSequenceOptions() {
		for (Dialect dialect : dialects) {
			for (IdentityGenerationType generation : IdentityGenerationType.values()) {
				Column column = new Column("ID").setDataType(DataType.BIGINT).setIdentity(true)
						.setIdentityGenerationType(generation).setIdentityStartValue(10L).setIdentityStep(2L);
				String sql = dialect.createSqlBuilder().definition(column).toString();
				assertTrue(sql.contains(
						"GENERATED " + (generation == IdentityGenerationType.ByDefault ? "BY DEFAULT" : "ALWAYS")
								+ " AS IDENTITY (START WITH 10 INCREMENT BY 2)"),
						sql);
				column.setIdentityStep(0L);
				assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().definition(column));
			}
		}
	}

	@Test
	void emitsGeneratedExpressionsAndRejectsConflictingDefaults() {
		for (Dialect dialect : dialects) {
			Column column = new Column("TOTAL").setDataType(DataType.INT).setFormula("PRICE * QUANTITY");
			String sql = dialect.createSqlBuilder().definition(column).toString();
			assertTrue(sql.contains("INTEGER GENERATED ALWAYS AS (PRICE * QUANTITY)"), sql);
			column.setDefaultValue("0");
			assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().definition(column));
		}
	}

	@Test
	void preservesAdvancedIdentityOptionsAndRejectsInvalidCache() {
		for (Dialect dialect : dialects) {
			Column column = new Column("ID").setDataType(DataType.BIGINT).setIdentity(true).setIdentityMinValue(1)
					.setIdentityMaxValue(999).setIdentityCacheSize(20).setIdentityCycle(true);
			String sql = dialect.createSqlBuilder().definition(column).toString();
			assertTrue(sql.contains("MINVALUE 1 MAXVALUE 999 CYCLE CACHE 20"), sql);
			column.setIdentityCache(false);
			assertTrue(dialect.createSqlBuilder().definition(column).toString().contains("NO CACHE"));
			column.setIdentityCacheSize(-1);
			assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().definition(column));
		}
	}

	@Test
	void usesHanaAddDropAndAlterColumnSyntax() {
		for (Dialect dialect : dialects) {
			Table original = new Table("ITEMS").setDialect(dialect).setSchemaName("Odd Schema");
			original.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			Table target = original.clone();
			target.getColumns().add("AMOUNT", c -> c.setDataType(DataType.NVARCHAR).setLength(40L));
			String addSql = alter(dialect, original, target);
			assertTrue(addSql.contains("ADD (AMOUNT NVARCHAR(40))"), addSql);
			assertTrue(alter(dialect, target, original).contains("DROP (AMOUNT)"));
			target = original.clone();
			target.getColumns().get("ID").setDataType(DataType.BIGINT).setNullable(false).setDefaultValue("1");
			String sql = alter(dialect, original, target);
			assertTrue(sql.contains("ALTER (ID BIGINT DEFAULT 1 NOT NULL)"), sql);
			String reverse = alter(dialect, target, original);
			assertTrue(reverse.contains("ALTER (ID INTEGER DEFAULT NULL NULL)"), reverse);
		}
	}

	@Test
	void usesStandaloneRenameColumnAndRejectsExpressionChanges() {
		for (Dialect dialect : dialects) {
			Table original = new Table("ITEMS").setDialect(dialect).setSchemaName("Odd Schema");
			original.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			Table target = original.clone();
			target.getColumns().get("ID").setName("NEW_ID");
			String sql = alter(dialect, original, target);
			assertTrue(sql.contains("RENAME COLUMN \"Odd Schema\".ITEMS.ID TO NEW_ID"), sql);
			target.getColumns().get("NEW_ID").setFormula("1 + 2");
			assertThrows(IllegalArgumentException.class, () -> alter(dialect, original, target));
		}
	}

	private String alter(Dialect dialect, Table original, Table target) {
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		return registry.createSql(original.diff(target), SqlType.ALTER).stream()
				.map(op -> op.getSqlText().replaceAll("\\s+", " ").replace("( ", "("))
				.collect(java.util.stream.Collectors.joining("; "));
	}

	@Test
	void renamesTableBeforeChangingColumns() {
		for (Dialect dialect : dialects) {
			Table original = new Table("ITEMS").setDialect(dialect).setSchemaName("Odd Schema");
			original.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			Table target = original.clone().setName("NEW_ITEMS");
			target.getColumns().get("ID").setDataType(DataType.BIGINT);
			String sql = alter(dialect, original, target);
			assertTrue(sql.startsWith("RENAME TABLE \"Odd Schema\".ITEMS TO \"Odd Schema\".NEW_ITEMS;"), sql);
			assertTrue(sql.contains("ALTER TABLE \"Odd Schema\".NEW_ITEMS ALTER (ID BIGINT)"), sql);
		}
	}

	@Test
	void qualifiesSchemaMovesAndRejectsUnqualifiedPlans() {
		for (Dialect dialect : dialects) {
			Table original = new Table("ITEMS").setDialect(dialect).setSchemaName("APP");
			original.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			Table target = original.clone().setSchemaName("ARCHIVE");
			target.getColumns().get("ID").setDataType(DataType.BIGINT);
			String sql = alter(dialect, original, target);
			assertTrue(sql.startsWith("RENAME TABLE APP.ITEMS TO ARCHIVE.ITEMS;"), sql);
			assertTrue(sql.contains("ALTER TABLE ARCHIVE.ITEMS ALTER (ID BIGINT)"), sql);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
	}
}
