/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerAlterViewTest {
	private View view() {
		final var view = new View("ActiveUsers");
		view.setSchemaName("Sales");
		view.setStatement("SELECT u.id AS id FROM Users u");
		return view;
	}

	@Test
	void replacesQueryThroughXmlAndReturnsAlterOperation() throws Exception {
		final var original = view();
		final var target = original.clone();
		target.setStatement("SELECT u.id AS id FROM Users u WHERE u.active");
		final View restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals(SqlType.ALTER, operations.get(0).getSqlType());
		assertEquals(
				"CREATE OR REPLACE VIEW `Sales`.`ActiveUsers` SQL SECURITY INVOKER AS "
						+ "SELECT u.id AS id FROM Users u WHERE u.active",
				operations.get(0).getSqlText().replaceAll("\\s+", " "));
		assertEquals(operations.get(0).getSqlText(), registry.createSql(restored, SqlType.ALTER).get(0).getSqlText());
	}

	@Test
	void replacesSecurityAndDoesNotReplaceSemanticNoOps() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = view();
		final var target = original.clone();
		target.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "DEFINER");
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.contains("SQL SECURITY DEFINER"));
		final var same = target.clone();
		same.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "definer");
		assertTrue(registry.createSql(target.diff(same), SqlType.ALTER).isEmpty());
		assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
		final var explicitDefault = original.clone();
		explicitDefault.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "INVOKER");
		assertTrue(registry.createSql(original.diff(explicitDefault), SqlType.ALTER).isEmpty());
	}

	@Test
	void rejectsUnsupportedDiffsRawDefinitionAndInvalidSecurity() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = view();
		final var renamed = original.clone().setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(renamed), SqlType.ALTER));
		final var source = original.clone();
		source.setDefinition("CREATE VIEW ActiveUsers SQL SECURITY INVOKER AS SELECT 1 AS id");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(source, SqlType.ALTER));
		final var invalid = original.clone();
		invalid.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, "unknown");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(invalid), SqlType.ALTER));
		final var missing = view();
		missing.setStatement(" \n ");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(missing, SqlType.ALTER));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(missing, SqlType.CREATE));
	}
}
