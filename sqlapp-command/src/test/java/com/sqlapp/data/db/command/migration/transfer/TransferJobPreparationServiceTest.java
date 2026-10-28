/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationTargetValidator;

/**
 * Uses disposable embedded HSQLDB only; no external database or copy execution.
 */
class TransferJobPreparationServiceTest {
	@TempDir
	Path directory;
	private final TransferJobPreparationService service = new TransferJobPreparationService();

	@Test
	void sourceConnectionFailureRetainsSqlCodesButNotDriverSecrets() throws Exception {
		final var source = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				final var failure = new SQLException("jdbc:private driver-secret");
				failure.setNextException(new SQLException("password-secret", "28P01", 23));
				throw failure;
			}
		};
		final var failure = assertThrows(TransferJobPreparationService.PreparationException.class,
				() -> service.prepare(job("tables: [ITEMS]\n"), Map.of("from", source, "to", database()), directory));
		assertEquals("opening source connection", failure.phase());
		assertEquals("28P01", failure.sqlState());
		assertEquals(23, failure.vendorCode());
		assertFalse(failure.getMessage().contains("driver-secret"));
		assertFalse(failure.getMessage().contains("password-secret"));
		assertNull(failure.getCause());
		assertEquals(0, failure.getSuppressed().length);
	}

	@Test
	void targetConnectionFailureDoesNotRetainSuppressedSourceCleanupSecrets() throws Exception {
		final var source = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() {
				return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
						java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class },
						(proxy, method, args) -> {
							if (method.getName().equals("close"))
								throw new SQLException("cleanup-secret jdbc:private");
							throw new UnsupportedOperationException();
						});
			}
		};
		final var target = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				throw new SQLException("driver-secret", "3D000", 7);
			}
		};
		final var failure = assertThrows(TransferJobPreparationService.PreparationException.class,
				() -> service.prepare(job("tables: [ITEMS]\n"), Map.of("from", source, "to", target), directory));
		assertEquals("opening target connection", failure.phase());
		assertEquals("3D000", failure.sqlState());
		assertNull(failure.getCause());
		assertEquals(0, failure.getSuppressed().length);
		assertFalse(failure.getMessage().contains("cleanup-secret"));
		assertFalse(failure.getMessage().contains("driver-secret"));
	}

	@Test
	void preparesPinnedPortableBulkJobWithTargetSchemaAndDependencyOrderWithoutCopyingRows() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ITEMS(ID))",
				"INSERT INTO ITEMS VALUES(1,'one')", "INSERT INTO ORDERS VALUES(10,1)");
		sql(target, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA",
				"CREATE TABLE ARCHIVE.ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))",
				"CREATE TABLE ARCHIVE.ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ARCHIVE.ITEMS(ID))");
		final Path job = job("tables: [ORDERS, ITEMS]\ntargetSchema: ARCHIVE\nchunkSize: 250\n");
		final byte[] original = Files.readAllBytes(job);
		final var prepared = service.prepare(job, Map.of("from", source, "to", target), directory);
		assertArrayEquals(original, Files.readAllBytes(job));
		assertEquals(List.of("transfer-table-2", "transfer-table-1"), prepared.taskIds());
		assertTrue(Files.isRegularFile(prepared.sourceSchemaFile()));
		assertEquals(prepared.job(), new TransferJobConfigurationIO().read(prepared.job().file()));
		final var configuration = new BulkMigrationJobConfigurationEditorIO().validate(prepared.dataJob().text());
		assertEquals("source-schema.xml", configuration.getSchemaFile());
		assertNotNull(configuration.getSchemaFingerprint());
		assertTrue(configuration.getVerification().isEnabled());
		assertTrue(configuration.getTasks().stream().allMatch(task -> task.getChunkSize() == 250 && !task.isResume()
				&& task.isRequireEmptyTarget() && task.getTargetTable().contains("ARCHIVE.")));
		assertFalse(prepared.dataJob().text().contains("jdbc:"));
		assertFalse(Files.readString(prepared.sourceSchemaFile()).contains("<row>"));
		final Path moved = directory.resolve("moved");
		Files.move(prepared.directory(), moved);
		try (var connection = source.getConnection(); var targetConnection = target.getConnection()) {
			final var resolved = new BulkMigrationJobConfigurationResolver()
					.resolveJob(moved.resolve("data-job.yaml").toFile(), connection, prepared.dataJob().fingerprint());
			assertEquals(prepared.planFingerprint(), resolved.plan().getFingerprint());
			BulkMigrationTargetValidator.validate(targetConnection, resolved.plan());
		}
		assertEquals(1, count(source, "ITEMS"));
		assertEquals(1, count(source, "ORDERS"));
		assertEquals(0, count(target, "ARCHIVE.ITEMS"));
		assertEquals(0, count(target, "ARCHIVE.ORDERS"));
	}

	@Test
	void missingBindingsAndUnsupportedPoliciesFailBeforeBorrowingConnectionsOrCreatingArtifacts() throws Exception {
		final var borrowed = new AtomicInteger();
		final var neverConnect = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				borrowed.incrementAndGet();
				throw new SQLException("driver-secret jdbc:private");
			}
		};
		final var bindings = Map.of("from", neverConnect, "to", neverConnect);
		final Path job = job("tables: [ITEMS]\n");
		assertThrows(IllegalArgumentException.class,
				() -> service.prepare(job, Map.of("from", neverConnect), directory));
		Files.writeString(job, Files.readString(job) + "mappingFile: mapping.yaml\n");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> service.prepare(job, bindings, directory))
				.getMessage().contains("mappingFile"));
		assertEquals(0, borrowed.get());
		try (var files = Files.list(directory)) {
			assertEquals(1, files.count());
		}
		Files.writeString(job, Files.readString(job).replace("mappingFile: mapping.yaml\n", ""));
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> service.prepare(job, bindings, directory));
		assertFalse(failure.getMessage().contains("driver-secret"));
		assertFalse(failure.getMessage().contains("jdbc:private"));
		assertNull(failure.getCause());
	}

	@Test
	void missingOrNonemptyTargetIsRejectedAndNoTargetTableIsCreated() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		final Path job = job("tables: [ITEMS]\n");
		final var bindings = Map.of("from", source, "to", target);
		final var missing = assertThrows(IllegalArgumentException.class,
				() -> service.prepare(job, bindings, directory));
		assertTrue(missing.getMessage().contains("empty-target conditions"));
		assertTrue(missing.getMessage().contains("target table was not found"));
		assertTrue(missing.getMessage().contains("ITEMS"));
		try (var connection = target.getConnection();
				var tables = connection.getMetaData().getTables(null, "PUBLIC", "ITEMS", null)) {
			assertFalse(tables.next());
		}
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(9)");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> service.prepare(job, bindings, directory))
				.getMessage().contains("must be empty"));
		assertEquals(1, count(source, "ITEMS"));
		assertEquals(1, count(target, "ITEMS"));
	}

	@Test
	void aliasesOfSameTableAndMissingSourceSchemaCannotProducePreparedJob() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final Path job = job("tables: [ITEMS, PUBLIC.ITEMS]\n");
		final var bindings = Map.of("from", source, "to", target);
		assertThrows(IllegalArgumentException.class, () -> service.prepare(job, bindings, directory));
		Files.writeString(job,
				Files.readString(job).replace("[ITEMS, PUBLIC.ITEMS]", "[ITEMS]") + "sourceSchema: MISSING\n");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> service.prepare(job, bindings, directory))
				.getMessage().contains("sourceSchema"));
	}

	private Path job(String options) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\n" + "schemaMode: REQUIRE_EXISTING\n" + options);
	}

	private static JDBCDataSource database() {
		final var source = new JDBCDataSource();
		source.setUrl("jdbc:hsqldb:mem:transfer_prepare_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		return source;
	}

	private static void sql(JDBCDataSource source, String... statements) throws Exception {
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			for (String sql : statements)
				statement.execute(sql);
		}
	}

	private static int count(JDBCDataSource source, String table) throws Exception {
		try (var connection = source.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rows.next();
			return rows.getInt(1);
		}
	}
}
