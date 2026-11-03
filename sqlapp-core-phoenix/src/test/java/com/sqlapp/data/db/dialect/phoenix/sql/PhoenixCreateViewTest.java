/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixCreateViewTest {
	private View view() {
		final var view = new View("extended");
		view.setSchemaName("Sales");
		view.setStatement("SELECT * FROM base WHERE id = 100");
		return view;
	}

	@Test
	void createsDeclaredColumnsWithFamiliesAndArraysThroughXmlOnBothVersions() throws Exception {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			registry.getOptions().setQuateColumnName(true);
			registry.getOptions().setCreateIfNotExists(true);
			final var view = view();
			final var column = new Column("Extra Info").setDataType(DataType.VARCHAR).setArrayDimension(1)
					.setArrayDimensionUpperBound(5);
			column.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
			view.getColumns().add(column);
			view.getColumns().add(new Column("score").setDataType(DataType.INT));
			final View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.startsWith("CREATE VIEW IF NOT EXISTS \"Sales\".\"extended\""), sql);
			assertTrue(sql.contains("\"details\".\"Extra Info\" VARCHAR ARRAY[5]"), sql);
			assertTrue(sql.contains("\"score\" INTEGER"), sql);
			assertTrue(sql.replaceAll("\\s+", " ").endsWith("AS SELECT * FROM base WHERE id = 100"), sql);
			registry.getOptions().setCreateIfNotExists(false);
			assertFalse(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("IF NOT EXISTS"));
		}
	}

	@Test
	void keepsMetadataViewsGuardedAndAcceptsCompleteDefinitions() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var view = view();
		view.getColumns().add(new Column("extra").setDataType(DataType.INT));
		view.getSpecifics().put("phoenix.viewMetadataOnly", true);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		view.setDefinition("CREATE VIEW extended (extra INTEGER) AS SELECT * FROM base");
		assertEquals(String.join("\n", view.getDefinition()),
				registry.createSql(view, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void rejectsUnrepresentableColumnsConstraintsAndBlankStatements() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var column : java.util.List.of(new Column("extra").setDataType(DataType.INT).setNotNull(true),
				new Column("extra").setDataType(DataType.INT).setDefaultValue("1"),
				new Column("extra").setDataType(DataType.INT).setIdentity(true),
				new Column("extra").setDataType(DataType.INT).setFormula("id + 1"),
				new Column("extra").setDataType(DataType.INT).setOnUpdate("1"))) {
			final var view = view();
			view.getColumns().add(column);
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		}
		final var view = view();
		view.getConstraints().add(new CheckConstraint("c", "id > 0"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(view, SqlType.CREATE));
		final var blank = view();
		blank.setStatement(" \n ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(blank, SqlType.CREATE));
	}
}
