/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixTableSafetyOptionsTest {
	private Table table() {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("VALUE").setDataType(DataType.INT));
		return table;
	}

	@Test
	void createsExplicitWalAndAppendOnlyOptionsThroughXmlWithoutChangingDefaults() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect4_8, DialectHolder.defaultDialect4_9,
				DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (String value : List.of("TrUe", "FALSE")) {
				final var table = table();
				table.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, value);
				table.getSpecifics().put(PhoenixDdlOptions.APPEND_ONLY_SCHEMA, value);
				final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				final var registry = dialect.createSqlFactoryRegistry();
				final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
				assertTrue(
						sql.contains(
								"DISABLE_WAL=" + value.toUpperCase() + ", APPEND_ONLY_SCHEMA=" + value.toUpperCase()),
						sql);
				assertFalse(registry.createSql(table(), SqlType.CREATE).get(0).getSqlText().contains("DISABLE_WAL"));
			}
		}
	}

	@Test
	void changesWalBothDirectionsAndCombinesColumnAndImmutableChanges() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_3,
				DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (boolean value : new boolean[] { true, false }) {
				final var original = table();
				original.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, !value);
				final var target = original.clone();
				target.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, value);
				assertEquals("ALTER TABLE \"Sales\".\"Events\" SET DISABLE_WAL=" + (value ? "TRUE" : "FALSE"),
						registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText());
				target.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, true);
				target.getColumns().add(new Column("EXTRA").setDataType(DataType.INT));
				assertEquals(3, registry.createSql(original.diff(target), SqlType.ALTER).size());
			}
			final var original = table();
			original.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, "true");
			final var target = original.clone();
			target.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, "TRUE");
			assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).isEmpty());
			target.getSpecifics().remove(PhoenixDdlOptions.DISABLE_WAL);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
	}

	@Test
	void respectsAppendOnlyVersionAndColumnDeletionRestrictions() {
		final var table = table();
		table.getSpecifics().put(PhoenixDdlOptions.APPEND_ONLY_SCHEMA, true);
		for (int minor : new int[] { 3, 7, 8, 9 }) {
			final var dialect = (Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 4, minor, 0);
			assertEquals(minor >= 8, dialect.supportsAppendOnlySchema());
			if (minor < 8)
				assertThrows(UnsupportedOperationException.class,
						() -> dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		}
		final var registry = DialectHolder.defaultDialect4_8.createSqlFactoryRegistry();
		final var target = table.clone();
		target.getColumns().remove("VALUE");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table.diff(target), SqlType.ALTER));
		final var added = table.clone();
		added.getColumns().add(new Column("EXTRA").setDataType(DataType.INT));
		assertEquals(1, registry.createSql(table.diff(added), SqlType.ALTER).size());
		added.getSpecifics().put(PhoenixDdlOptions.APPEND_ONLY_SCHEMA, false);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table.diff(added), SqlType.ALTER));
	}

	@Test
	void rejectsInvalidBooleansRatherThanDroppingDeclarations() {
		final var registry = DialectHolder.defaultDialect4_8.createSqlFactoryRegistry();
		for (String key : List.of(PhoenixDdlOptions.DISABLE_WAL, PhoenixDdlOptions.APPEND_ONLY_SCHEMA)) {
			for (String invalid : List.of("", " ", "maybe", "1", "true; DROP TABLE T")) {
				final var table = table();
				table.getSpecifics().put(key, invalid);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(table().diff(table), SqlType.ALTER));
			}
		}
	}
}
