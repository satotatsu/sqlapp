/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * End-to-end generated YAML, planning, copying and verification against
 * isolated HSQLDB only.
 */
class SchemaDraftMigrationWorkflowTest {
	@TempDir
	Path directory;

	@Test
	void generatedDraftCanBeSavedPlannedExecutedAndVerifiedWithoutAdditionalSettings() throws Exception {
		final String source = "jdbc:hsqldb:mem:draft_source_" + UUID.randomUUID();
		final String target = "jdbc:hsqldb:mem:draft_target_" + UUID.randomUUID();
		for (final String url : List.of(source, target)) {
			try (var connection = DriverManager.getConnection(url, "SA", "");
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
				if (url.equals(source))
					statement.execute("INSERT INTO ITEMS VALUES(1, 'one'), (2, 'two')");
			}
		}
		final var schema = new Schema("PUBLIC");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		final Path xml = directory.resolve("schema.xml");
		schema.writeXml(xml.toFile());
		final var builder = new BulkMigrationJobDraftBuilder();
		final String yaml = builder.draft(builder.inspect(xml), List.of("PUBLIC.ITEMS"), "gui-draft", 1, true);
		final var saved = new BulkMigrationJobConfigurationEditorIO().write(directory.resolve("job.yaml"), yaml, null);
		final var sourceSpec = new PlanningJdbc.Spec(source, "SA", "");
		final var targetSpec = new PlanningJdbc.Spec(target, "SA", "");
		final var request = new DataMigrationPlanningService.Request(saved.file(), saved.fingerprint());
		final var planner = new DataMigrationPlanningService();
		final var reviewed = planner.plan(request, sourceSpec, new char[0], targetSpec, new char[0]);
		assertNull(DataMigrationExecutionService.restriction(reviewed));
		assertNotNull(reviewed.executionSnapshot().pauseRestriction());
		final var result = new DataMigrationExecutionService().execute(reviewed, sourceSpec, new char[0], targetSpec,
				new char[0]);
		assertTrue(result.successful(), result.review().summary());
		assertEquals(2, result.execution().processedRows());
		assertTrue(result.verificationResult().isMatch());
		try (var connection = DriverManager.getConnection(target, "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			rows.next();
			assertEquals(2, rows.getInt(1));
		}
		assertThrows(IllegalArgumentException.class,
				() -> planner.plan(request, sourceSpec, new char[0], targetSpec, new char[0]));
	}
}
