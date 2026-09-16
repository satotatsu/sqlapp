/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.SchemaCompatibility;

class MigrationSchemaDriftAssessorTest {

	@Test
	void reportsMissingAndNarrowerLiveObjectsAsBreaking() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration_drift", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE PRESENT(NAME VARCHAR(20))");
			}
			final Table present = new Table("PRESENT");
			present.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(100L));
			final Table missing = new Table("MISSING");
			missing.getColumns().add(new Column("ID").setDataType(DataType.BIGINT));

			final var report = MigrationSchemaDriftAssessor.assess(connection, List.of(present, missing));

			assertEquals(SchemaCompatibility.BREAKING, report.compatibility());
			assertEquals(2, report.changes().stream()
					.filter(change -> change.compatibility() == SchemaCompatibility.BREAKING).count());
		}
	}
}
