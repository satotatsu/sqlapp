/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class TransferDefaultConversionSettingsTest {
	private final TransferJobConfigurationIO io = new TransferJobConfigurationIO();
	private static final String JOB = """
			formatVersion: 1
			source: source
			target: target
			tables: [DEFAULTS]
			schemaMode: CREATE_MISSING
			dataMode: SCHEMA_ONLY
			""";

	@Test
	void readsAndWritesTheDefaultConversionSection() {
		final var configuration = io.validate(JOB + """
				defaultConversion:
				  sourceProductName: Microsoft SQL Server
				  sourceProductMajorVersion: 16
				  strict: true
				  columns:
				    - {table: DEFAULTS, column: COMPUTED, replace: '3'}
				    - {table: DEFAULTS, column: CREATED, remove: true}
				""");
		final var settings = configuration.defaultConversion();
		assertEquals("Microsoft SQL Server", settings.sourceProductName());
		assertEquals(16, settings.sourceProductMajorVersion());
		assertTrue(settings.strictConversion());
		assertEquals(2, settings.columns().size());
		assertEquals(configuration, io.validate(io.format(configuration)));
		assertNull(io.validate(JOB).defaultConversion());
		assertFalse(io.format(io.validate(JOB)).contains("defaultConversion"));
	}

	@Test
	void rejectsAmbiguousColumnDecisions() {
		for (final String invalid : List.of("- {table: T, column: C, replace: '1', remove: true}",
				"- {table: T, column: C}", "- {table: T, column: C, remove: false}",
				"- {table: T, column: C, replace: ' '}", "- {table: T, replace: '1'}",
				"- {table: T, column: C, replace: '1'}\n    - {table: T, column: C, remove: true}",
				"- {table: T, column: C, unknown: 1}"))
			assertThrows(IllegalArgumentException.class,
					() -> io.validate(JOB + "defaultConversion:\n  columns:\n    " + invalid + "\n"), invalid);
		assertThrows(IllegalArgumentException.class,
				() -> io.validate(JOB + "defaultConversion: {sourceProductMajorVersion: 16}\n"));
	}

	private static Table table() {
		final var table = new Table("DEFAULTS");
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add("LABEL",
				column -> column.setDataType(DataType.NVARCHAR).setLength(10L).setDefaultValue("N'a''b'"));
		table.getColumns().add("CREATED",
				column -> column.setDataType(DataType.TIMESTAMP).setLength(6L).setDefaultValue("getdate()"));
		table.getColumns().add("COMPUTED", column -> column.setDataType(DataType.INT).setDefaultValue("(1)+(2)"));
		table.getColumns().add("PLAIN", column -> column.setDataType(DataType.INT));
		return table;
	}

	private static TransferSchemaPreparation.Plan plan(final Table table,
			final TransferJobConfiguration.DefaultConversion settings) throws Exception {
		final var schema = new Schema("dbo");
		schema.getTables().add(table);
		final var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:default_settings_" + UUID.randomUUID());
		ds.setUser("SA");
		try (var connection = ds.getConnection()) {
			final var plan = TransferSchemaPreparation.schemaOnly(connection, schema,
					List.of(new TransferSchemaPreparation.Selection("defaults", table,
							table.clone().setSchemaName("PUBLIC"))),
					"fixture", TransferJobConfiguration.CompatibilityPolicy.WARN,
					TransferJobConfiguration.SchemaMode.CREATE_MISSING, null, settings);
			TransferSchemaPreparation.execute(connection, plan, progress -> {
			});
			return plan;
		}
	}

	private static TransferJobConfiguration.DefaultConversion settings(final Boolean strict,
			final TransferJobConfiguration.ColumnDefault... columns) {
		return new TransferJobConfiguration.DefaultConversion("Microsoft SQL Server", 16, strict, List.of(columns));
	}

	@Test
	void explicitProductAndColumnDecisionsApply() throws Exception {
		final Table table = table();
		final var plan = plan(table,
				settings(null, new TransferJobConfiguration.ColumnDefault("DEFAULTS", "COMPUTED", "3", null),
						new TransferJobConfiguration.ColumnDefault("DEFAULTS", "CREATED", null, true),
						new TransferJobConfiguration.ColumnDefault("DEFAULTS", "PLAIN", "7", null)));
		final var byColumn = new java.util.HashMap<String, TransferDefaultConversion>();
		plan.defaults().forEach(conversion -> byColumn.put(conversion.column(), conversion));
		assertEquals("'a''b'", byColumn.get("LABEL").targetExpression());
		assertEquals(Outcome.REPLACED, byColumn.get("COMPUTED").outcome());
		assertEquals("3", byColumn.get("COMPUTED").targetExpression());
		assertEquals(Outcome.REMOVED, byColumn.get("CREATED").outcome());
		assertEquals(Outcome.REPLACED, byColumn.get("PLAIN").outcome());
		assertEquals(0, TransferSchemaChangeChecks.openDefaultCount(plan));
		final String ddl = String.join("\n", plan.statements().stream().map(statement -> statement.sql()).toList())
				.toUpperCase(Locale.ROOT);
		assertTrue(ddl.contains("DEFAULT 3"), ddl);
		assertTrue(ddl.contains("DEFAULT 7"), ddl);
		assertEquals("(1)+(2)", table.getColumns().get("COMPUTED").getDefaultValue());
	}

	@Test
	void rejectsColumnDecisionsForUnknownColumns() {
		final var failure = assertThrows(TransferSchemaPreparation.ValidationException.class, () -> plan(table(),
				settings(null, new TransferJobConfiguration.ColumnDefault("DEFAULTS", "MISSING", "1", null))));
		assertTrue(failure.getMessage().contains("DEFAULTS.MISSING"), failure.getMessage());
		assertThrows(TransferSchemaPreparation.ValidationException.class, () -> plan(table(),
				new TransferJobConfiguration.DefaultConversion("No Such Database", null, null, null)));
	}

	@Test
	void strictModeStopsAfterAnalyzingEveryDefault() throws Exception {
		final var failure = assertThrows(TransferSchemaPreparation.ValidationException.class,
				() -> plan(table(), settings(true)));
		assertTrue(failure.getMessage().contains("2 open finding(s)"), failure.getMessage());
		assertTrue(
				failure.getMessage().contains("DEFAULTS.CREATED") && failure.getMessage().contains("DEFAULTS.COMPUTED"),
				failure.getMessage());
		final var resolved = plan(table(),
				settings(true, new TransferJobConfiguration.ColumnDefault("DEFAULTS", "COMPUTED", "3", null),
						new TransferJobConfiguration.ColumnDefault("DEFAULTS", "CREATED", null, true)));
		assertEquals(0, TransferSchemaChangeChecks.openDefaultCount(resolved));
	}
}
