/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerCommitTimestampMigrationTest {
	private Table table() {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("Updated At").setDataType(DataType.TIMESTAMP).setNotNull(true));
		return table;
	}

	@Test
	void enablesAndRemovesThroughXmlWithoutRestatingDefaultOrNullability() throws Exception {
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals("ALTER TABLE `Sales`.`Events` ALTER COLUMN `Updated At` SET OPTIONS (allow_commit_timestamp=true)",
				operations.get(0).getSqlText());
		final var removed = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, removed.size());
		assertTrue(removed.get(0).getSqlText().endsWith("SET OPTIONS (allow_commit_timestamp=null)"));
		final var disabled = restored.clone();
		disabled.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, false);
		assertEquals(removed.get(0).getSqlText(),
				registry.createSql(restored.diff(disabled), SqlType.ALTER).get(0).getSqlText());
		assertTrue(registry.createSql(original.diff(disabled), SqlType.ALTER).isEmpty());
		assertTrue(restored.getColumns().get(0).isNotNull());
	}

	@Test
	void treatsCaseOnlyChangesAsNoOpAndDoesNotRewriteOtherClauses() {
		final var original = table();
		original.getColumns().get(0).setDefaultValue("TIMESTAMP '2000-01-01T00:00:00Z'");
		original.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.LOCALITY_GROUP, "hot");
		original.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, "true");
		final var target = original.clone();
		target.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, "TRUE");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).isEmpty());
		target.getColumns().get(0).getSpecifics().remove(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP);
		final String sql = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
		assertFalse(sql.contains("DEFAULT"), sql);
		assertFalse(sql.contains("NOT NULL"), sql);
		assertFalse(sql.contains("locality_group"), sql);
	}

	@Test
	void rejectsMalformedFlagsForCreateAndAlter() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String value : new String[] { "yes", "1", "", "true; DROP TABLE t" }) {
			final var original = table();
			final var target = original.clone();
			target.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, value);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(target, SqlType.CREATE));
		}
	}

	@Test
	void rejectsWrongTypesKeysAndMixedChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (int mode = 0; mode < 4; mode++) {
			final var original = table();
			switch (mode) {
			case 0 -> original.getColumns().get(0).setDataType(DataType.BIGINT);
			case 1 -> original.getColumns().get(0).setArrayDimension(1);
			case 2 -> original.getConstraints().addPrimaryKeyConstraint("pk", original.getColumns().get(0));
			case 3 -> original.getColumns().get(0).setFormula("CURRENT_TIMESTAMP()");
			}
			final var target = original.clone();
			target.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
			final Class<? extends RuntimeException> expected = mode < 2 ? IllegalArgumentException.class
					: UnsupportedOperationException.class;
			assertThrows(expected, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		target.getColumns().get(0).setNotNull(false);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}
}
