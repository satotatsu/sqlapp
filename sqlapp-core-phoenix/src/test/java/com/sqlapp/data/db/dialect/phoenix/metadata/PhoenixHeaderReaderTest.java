/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class PhoenixHeaderReaderTest {
	private Map<String, Object> row() {
		final var row = new HashMap<String, Object>();
		row.put("TABLE_CAT", "tenant");
		row.put("TABLE_SCHEM", "app");
		row.put("TABLE_NAME", "filtered");
		row.put("REMARKS", "comment");
		row.put("VIEW_STATEMENT", "SELECT * FROM base WHERE k = 'a b'");
		row.put("VIEW_TYPE", "UPDATABLE");
		row.put("SALT_BUCKETS", 8L);
		row.put("IMMUTABLE_ROWS", true);
		row.put("MULTI_TENANT", false);
		return row;
	}

	@Test
	void retainsViewSourceInOneJdbcCallAndRequiresCompleteDdlForRecreation() throws Exception {
		final var jdbc = new PhoenixColumnReaderTest.Jdbc(List.of(row()), "getTables");
		final var reader = new PhoenixViewReader(DialectHolder.defaultDialect);
		reader.setReaderOptions(new ReaderOptions());
		final var view = reader.doGetAll(jdbc.connection, new ParametersContext(), null).get(0);
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.closed);
		assertInstanceOf(View.class, view);
		assertEquals("SELECT * FROM base WHERE k = 'a b'", String.join("\n", view.getStatement()));
		final View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(restored, SqlType.CREATE));
		restored.setDefinition("CREATE VIEW app.filtered AS SELECT * FROM base WHERE k = 'a b'");
		assertEquals(String.join("\n", restored.getDefinition()),
				registry.createSql(restored, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void honorsHeaderOnlyReadsAndRetainsTableOptions() {
		final var options = new ReaderOptions();
		options.setReadStatement(false);
		options.setReadDefinition(false);
		final var reader = new PhoenixViewReader(DialectHolder.defaultDialect);
		reader.setReaderOptions(options);
		final var jdbc = new PhoenixColumnReaderTest.Jdbc(List.of(row()), "getTables");
		assertTrue(reader.doGetAll(jdbc.connection, new ParametersContext(), null).get(0).getStatement().isEmpty());
		final var tables = new PhoenixTableReader(DialectHolder.defaultDialect);
		tables.setReaderOptions(new ReaderOptions());
		final var tableJdbc = new PhoenixColumnReaderTest.Jdbc(List.of(row()), "getTables");
		final var table = tables.doGetAll(tableJdbc.connection, new ParametersContext(), null).get(0);
		assertEquals("8", table.getSpecifics().get("phoenix.saltBuckets"));
		assertEquals("true", table.getSpecifics().get("phoenix.immutableRows"));
		assertEquals("false", table.getSpecifics().get("phoenix.multiTenant"));
		assertTrue(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText().contains("SALT_BUCKETS=8"));
	}

	@Test
	void createsUserAuthoredSimpleViewsAndHonorsExistenceOptions() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final var view = new View("simple");
		view.setStatement("SELECT * FROM base");
		assertTrue(
				registry.createSql(view, SqlType.CREATE).get(0).getSqlText().startsWith("CREATE VIEW IF NOT EXISTS"));
		assertTrue(registry.createSql(view, SqlType.DROP).get(0).getSqlText().startsWith("DROP VIEW IF EXISTS"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new View("empty"), SqlType.CREATE));
	}
}
