/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.SchemaCompatibility;

class MigrationPreSchemaDriftTest {
	@TempDir
	Path directory;
	private Path up;
	private final JDBCDataSource dataSource = new JDBCDataSource();

	@BeforeEach
	void initialize() throws Exception {
		up = Files.createDirectory(directory.resolve("up"));
		dataSource.setUrl("jdbc:hsqldb:mem:pre_drift_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO PRESENT VALUES('ok');");
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE PRESENT(NAME VARCHAR(20))");
		}
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(up.toFile());
		return command;
	}

	private Path schema(final long length, final boolean missingTable) throws Exception {
		final Schema schema = new Schema("PUBLIC");
		final Table present = new Table("PRESENT");
		present.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(length));
		schema.getTables().add(present);
		if (missingTable) {
			final Table missing = new Table("MISSING");
			missing.getColumns().add(new Column("ID").setDataType(DataType.INT));
			schema.getTables().add(missing);
		}
		final Path file = directory.resolve("expected-" + length + "-" + missingTable + ".xml");
		schema.writeXml(file.toFile());
		return file;
	}

	private long count(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	@Test
	void remainsDisabledWithoutConfiguration() {
		final var command = new MigrationCommand();
		assertNull(command.getPreMigrationSchemaFile());
		assertNull(command.getSchemaDriftReport());
	}

	@Test
	void breakingDriftStopsBeforeHistoryOrSetupMutation() throws Exception {
		final var setup = Files.createDirectory(directory.resolve("setup"));
		Files.writeString(setup.resolve("setup.sql"), "INSERT INTO PRESENT VALUES('setup');");
		final var command = configure(new MigrationCommand());
		command.setSetupSqlDirectory(setup.toFile());
		command.setPreMigrationSchemaFile(schema(100, true).toFile());
		assertThrows(RuntimeException.class, command::run);
		assertEquals(SchemaCompatibility.BREAKING, command.getSchemaDriftReport().compatibility());
		assertEquals(0, count("SELECT COUNT(*) FROM PRESENT"));
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='changelog'"));
	}

	@Test
	void compatiblePreconditionAllowsMigration() throws Exception {
		final var command = configure(new MigrationCommand());
		command.setPreMigrationSchemaFile(schema(20, false).toFile());
		command.run();
		assertNotNull(command.getSchemaDriftReport());
		assertFalse(command.getSchemaDriftReport().compatibility() == SchemaCompatibility.BREAKING);
		assertEquals(1, count("SELECT COUNT(*) FROM PRESENT"));
	}

	@Test
	void missingOrEmptySchemaFileFailsClearlyBeforeHistoryCreation() throws Exception {
		final var missing = configure(new MigrationCommand());
		missing.setPreMigrationSchemaFile(directory.resolve("missing.xml").toFile());
		assertThrows(RuntimeException.class, missing::run);
		final Schema empty = new Schema("PUBLIC");
		final Path emptyFile = directory.resolve("empty.xml");
		empty.writeXml(emptyFile.toFile());
		final var noTables = configure(new MigrationCommand());
		noTables.setPreMigrationSchemaFile(emptyFile.toFile());
		assertThrows(RuntimeException.class, noTables::run);
	}
}
