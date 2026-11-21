/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.phoenix.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixFunctionalIndexTest {
	private Table table(boolean view) {
		final Table table = view ? new View("People").setStatement("SELECT * FROM base") : new Table("People");
		table.setSchemaName("Sales");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("Name").setDataType(DataType.VARCHAR));
		table.getColumns().get("Name").getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
		return table;
	}

	private Index index() {
		final var index = new Index("upper_name").setIndexType(IndexType.Function);
		index.getColumns().add("UPPER(\"details\".\"Name\" || ' 日本語')", Order.Desc);
		index.getColumns().add("ID");
		index.getIncludes().add("Name");
		return index;
	}

	@Test
	void preservesExpressionsAndOrdinaryColumnsThroughXmlAndNativeCreate() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect4_3, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (boolean view : new boolean[] { false, true }) {
				final var table = table(view);
				table.getIndexes().add(index());
				final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
				final var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setQuateColumnName(true);
				registry.getOptions().setDecorateSchemaName(true);
				final String sql = registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
				assertTrue(sql.contains("UPPER(\"details\".\"Name\" || ' 日本語') DESC"), sql);
				assertTrue(sql.matches("(?s).*,\\s*ID\\).*"), sql);
				assertTrue(sql.matches("(?s).*INCLUDE \\(\\s*\"details\"\\.\"Name\"\\s*\\).*"), sql);
				assertTrue(sql.contains("ON \"Sales\".\"People\""), sql);
			}
		}
	}

	@Test
	void addsAndReplacesFunctionalIndexesThroughTableAndViewDiffs() {
		for (boolean view : new boolean[] { false, true }) {
			final var original = table(view);
			final var target = original.clone();
			target.getIndexes().add(index());
			final var registry = DialectHolder.defaultDialect4_3.createSqlFactoryRegistry();
			assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
			final var replacement = target.clone();
			replacement.getIndexes().get(0).getColumns().clear();
			replacement.getIndexes().get(0).getColumns().add("LOWER(\"details\".\"Name\")");
			final var operations = registry.createSql(target.diff(replacement), SqlType.ALTER);
			assertEquals(2, operations.size());
			assertTrue(operations.get(0).getSqlText().startsWith("DROP INDEX"));
			assertTrue(operations.get(1).getSqlText().contains("LOWER(\"details\".\"Name\")"));
		}
	}

	@Test
	void resolvesTheFourThreeBoundaryWithoutEnablingLaterFeatures() {
		for (int minor : new int[] { 0, 2, 3, 7, 8, 9 }) {
			final var dialect = (Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 4, minor, 0);
			assertEquals(minor >= 3, dialect.supportsFunctionalIndexes());
			assertEquals(minor >= 8, dialect.supportsLimitOffset());
			assertEquals(minor >= 9, dialect.supportsColumnDefault());
			assertFalse(dialect.supportsValues());
		}
		final var table = table(false);
		table.getIndexes().add(index());
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(table.getIndexes().get(0), SqlType.CREATE));
	}

	@Test
	void rejectsAmbiguousFamilyDeclarationsAndColumnDeletesWithUnparsedDependencies() {
		final var registry = DialectHolder.defaultDialect4_3.createSqlFactoryRegistry();
		final var table = table(false);
		table.getIndexes().add(index());
		final var family = table.clone();
		family.getIndexes().get(0).getColumns().get(0).getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY,
				"details");
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(family.getIndexes().get(0), SqlType.CREATE));
		final var blank = table.clone();
		blank.getIndexes().get(0).getColumns().clear();
		blank.getIndexes().get(0).getColumns().add(" ");
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(blank.getIndexes().get(0), SqlType.CREATE));
		final var target = table.clone();
		target.getColumns().remove("Name");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table.diff(target), SqlType.ALTER));
		target.getIndexes().clear();
		final var operations = registry.createSql(table.diff(target), SqlType.ALTER);
		assertEquals(2, operations.size());
		assertTrue(operations.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN"));
	}
}
