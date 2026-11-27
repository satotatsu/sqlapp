/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferDatabaseCommandTest {
	@TempDir
	Path directory;

	@Test
	void reviewsWithoutCopyThenExecutesTheSamePinnedDefinitionAndWritesAudits() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var command = command(source, target);
		command.setCloseDataSource(false);
		command.run();
		assertNotNull(command.getReview());
		assertNull(command.getResult());
		assertNull(command.getResultFile());
		assertEquals(0, count(target));
		assertTrue(Files.readString(command.getReviewFile()).contains("planFingerprint"));
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(command.getReview().prepared().job().fingerprint());
		command.run();
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, command.getResult().phase());
		assertEquals(2, count(target));
		assertTrue(Files.readString(command.getResultFile()).contains("COMPLETE"));
		assertFalse(Files.readString(command.getReviewFile()).contains("jdbc:"));
		assertEquals(0, source.closes);
		assertEquals(0, target.closes);
	}

	@Test
	void failuresRetainCommittedWorkAndBothOwnedPoolsAreClosed() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY CHECK(ID<2))");
		final var command = command(source, target);
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint(
				new TransferJobConfigurationIO().read(command.getConfigurationFile().toPath()).fingerprint());
		assertThrows(TransferJobExecutionService.ExecutionException.class, command::run);
		assertEquals(TransferJobExecutionService.Phase.COPY, command.getResult().phase());
		assertEquals(1, count(target));
		assertTrue(Files.readString(command.getResultFile()).contains("committedProgress"));
		assertEquals(1, source.closes);
		assertEquals(1, target.closes);
	}

	@Test
	void validatesExecutionPinAndSharedPoolOwnershipWithoutOpeningInvalidJobs() throws Exception {
		final var shared = database();
		sql(shared, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var command = command(shared, shared);
		command.setAllowExecution(true);
		assertThrows(IllegalArgumentException.class, command::validateInputs);
		assertEquals(0, shared.closes);
		command.setAllowExecution(false);
		command.run();
		assertEquals(1, shared.closes);
		command.setAllowExecution(true);
		command.setExpectedConfigurationFingerprint("sha256:" + "a".repeat(64));
		assertThrows(IllegalArgumentException.class, command::validateInputs);
	}

	private TransferDatabaseCommand command(Owned source, Owned target) throws Exception {
		final var job = Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\nchunkSize: 1\n");
		final var command = new TransferDatabaseCommand();
		command.setConfigurationFile(job.toFile());
		command.setWorkspaceDirectory(directory.resolve("reports").toFile());
		command.setSourceDataSource(source);
		command.setDataSource(target);
		return command;
	}

	private static final class Owned extends JDBCDataSource implements AutoCloseable {
		int closes;

		public void close() {
			closes++;
		}
	}

	private static Owned database() {
		final var ds = new Owned();
		ds.setUrl("jdbc:hsqldb:mem:direct_command_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	private static void sql(JDBCDataSource ds, String... sql) throws Exception {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (var text : sql)
				s.execute(text);
		}
	}

	private static int count(JDBCDataSource ds) throws Exception {
		try (var c = ds.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			r.next();
			return r.getInt(1);
		}
	}
}
