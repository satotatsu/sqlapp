/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.defaultvalue.DefaultValueMigration.Outcome;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class TransferDefaultConversionTest {

	private static Table sqlServerTable() {
		final var table = new Table("DEFAULTS");
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add("LABEL",
				column -> column.setDataType(DataType.NVARCHAR).setLength(10L).setDefaultValue("N'a''b'"));
		table.getColumns().add("AMOUNT",
				column -> column.setDataType(DataType.DECIMAL).setLength(5L).setScale(2).setDefaultValue("1.50"));
		table.getColumns().add("FLAG", column -> column.setDataType(DataType.BOOLEAN).setDefaultValue("1"));
		table.getColumns().add("CREATED",
				column -> column.setDataType(DataType.TIMESTAMP).setLength(6L).setDefaultValue("getdate()"));
		table.getColumns().add("COMPUTED", column -> column.setDataType(DataType.INT).setDefaultValue("(1)+(2)"));
		table.setPrimaryKey("PK_DEFAULTS", table.getColumns().get("ID"));
		return table;
	}

	private static Schema schema(final String product, final Integer major, final Table table) {
		final var schema = new Schema("dbo").setProductName(product).setProductMajorVersion(major);
		schema.getTables().add(table);
		return schema;
	}

	private static JDBCDataSource database() {
		final var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:default_conversion_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	@Test
	void convertsDefaultsInPlannedSqlAndReportsOpenOnes() throws Exception {
		final Table table = sqlServerTable();
		final var schema = schema("Microsoft SQL Server", 16, table);
		final var selections = List
				.of(new TransferSchemaPreparation.Selection("defaults", table, table.clone().setSchemaName("PUBLIC")));
		final var target = database();
		try (var connection = target.getConnection()) {
			final var plan = TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN);
			assertEquals(plan, TransferSchemaPreparation.schemaOnly(connection, schema, selections, "fixture",
					TransferJobConfiguration.CompatibilityPolicy.WARN));
			final var byColumn = new java.util.HashMap<String, TransferDefaultConversion>();
			plan.defaults().forEach(conversion -> byColumn.put(conversion.column(), conversion));
			assertEquals(5, byColumn.size());
			assertEquals("'a''b'", byColumn.get("LABEL").targetExpression());
			assertEquals("1.50", byColumn.get("AMOUNT").targetExpression());
			assertEquals("TRUE", byColumn.get("FLAG").targetExpression());
			assertEquals(Outcome.REQUIRES_ACTION, byColumn.get("CREATED").outcome());
			assertEquals(Outcome.REQUIRES_ACTION, byColumn.get("COMPUTED").outcome());
			for (final var conversion : plan.defaults())
				assertEquals(TransferDefaultConversion.TransferImpact.NOT_APPLICABLE, conversion.transferImpact());
			assertEquals(2, TransferSchemaChangeChecks.openDefaultCount(plan));
			assertEquals(2, plan.warnings().stream()
					.filter(warning -> warning.startsWith(TransferSchemaChangeChecks.DEFAULT_UNRESOLVED)).count());

			final String ddl = String.join("\n", plan.statements().stream().map(statement -> statement.sql()).toList())
					.toUpperCase(Locale.ROOT);
			assertTrue(ddl.contains("DEFAULT 'A''B'"), ddl);
			assertTrue(ddl.contains("DEFAULT TRUE"), ddl);
			assertFalse(ddl.contains("GETDATE"), ddl);
			assertFalse(ddl.contains("(1)+(2)"), ddl);
			assertEquals("getdate()", table.getColumns().get("CREATED").getDefaultValue());

			TransferSchemaPreparation.execute(connection, plan, progress -> {
			});
			try (var statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO PUBLIC.DEFAULTS(ID) VALUES (1)");
				try (var rows = statement.executeQuery("SELECT * FROM PUBLIC.DEFAULTS")) {
					assertTrue(rows.next());
					assertEquals("a'b", rows.getString("LABEL"));
					assertEquals(new java.math.BigDecimal("1.50"), rows.getBigDecimal("AMOUNT"));
					assertTrue(rows.getBoolean("FLAG"));
					assertNull(rows.getObject("CREATED"));
					assertNull(rows.getObject("COMPUTED"));
				}
			}
		}
	}

	@Test
	void unknownSourceProductLeavesDefaultsOpen() throws Exception {
		final Table table = sqlServerTable();
		final var selections = List
				.of(new TransferSchemaPreparation.Selection("defaults", table, table.clone().setSchemaName("PUBLIC")));
		try (var connection = database().getConnection()) {
			final var plan = TransferSchemaPreparation.schemaOnly(connection, schema(null, null, table), selections,
					"fixture", TransferJobConfiguration.CompatibilityPolicy.WARN);
			assertEquals(5, TransferSchemaChangeChecks.openDefaultCount(plan));
			assertTrue(plan.defaults().stream().allMatch(
					conversion -> conversion.diagnostics().getFirst().code().equals("SOURCE_DIALECT_UNKNOWN")));
		}
	}

	@Test
	void copiedRowsDoNotDependOnOmittedDefaults() {
		final Table table = sqlServerTable();
		final var targets = Map.of(table.getName(), table.clone());
		final var warnings = new ArrayList<String>();
		final var conversions = TransferDefaultConversions.apply(
				DialectResolver.getInstance().getDialect("Microsoft SQL Server", 16, 0, 0),
				DialectResolver.getInstance().getDialect("PostgreSQL", 17, 0, 0),
				List.of(new TransferSchemaPreparation.Selection("defaults", table, table)), targets, true,
				TransferJobConfiguration.CompatibilityPolicy.WARN, warnings);
		assertTrue(conversions.stream().allMatch(
				conversion -> conversion.transferImpact() == TransferDefaultConversion.TransferImpact.INDEPENDENT));
		final var created = conversions.stream().filter(conversion -> conversion.column().equals("CREATED")).findFirst()
				.orElseThrow();
		assertEquals("CAST(statement_timestamp() AS timestamp)", created.targetExpression());
		assertEquals(Outcome.CONVERTED_APPROXIMATE, created.outcome());
		assertTrue(
				warnings.stream().anyMatch(warning -> warning.startsWith(TransferSchemaChangeChecks.DEFAULT_UNRESOLVED)
						&& warning.contains("COMPUTED") && warning.contains("before cutover")));
		assertTrue(
				warnings.stream().anyMatch(warning -> warning.startsWith(TransferSchemaChangeChecks.DEFAULT_CONVERTED)
						&& warning.contains("DEFAULT_TIME_BASIS")));
		assertEquals("CAST(statement_timestamp() AS timestamp)",
				targets.get(table.getName()).getColumns().get("CREATED").getDefaultValue());
	}

	@Test
	void keepsEquivalentExistingTargetSpelling() {
		final var dialect = DialectResolver.getInstance().getDialect("PostgreSQL", 17, 0, 0);
		final var existing = new Table("T");
		existing.getColumns().add("D",
				column -> column.setDataType(DataType.DATE).setDefaultValue("(statement_timestamp())::date"));
		existing.getColumns().add("X", column -> column.setDataType(DataType.INT).setDefaultValue("f()"));
		final var desired = new Table("T");
		desired.getColumns().add("D",
				column -> column.setDataType(DataType.DATE).setDefaultValue("CAST(statement_timestamp() AS date)"));
		desired.getColumns().add("X", column -> column.setDataType(DataType.INT).setDefaultValue("g()"));
		TransferDefaultConversions.keepEquivalentDefaults(existing, desired, dialect);
		assertEquals("(statement_timestamp())::date", desired.getColumns().get("D").getDefaultValue());
		assertEquals("g()", desired.getColumns().get("X").getDefaultValue());
	}
}
